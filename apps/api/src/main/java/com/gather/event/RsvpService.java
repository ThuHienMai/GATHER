package com.gather.event;

import com.gather.common.ApiException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RsvpService {
  private final EventService events;
  private final JdbcTemplate jdbc;
  private final AttendanceOperations attendance;

  public RsvpService(EventService events, JdbcTemplate jdbc, AttendanceOperations attendance) {
    this.events = events;
    this.jdbc = jdbc;
    this.attendance = attendance;
  }

  public record Participant(UUID userId, String firstName, String status) {}

  public record Summary(
      int goingCount,
      int maybeCount,
      int waitlistedCount,
      String myStatus,
      List<Participant> participants) {}

  public Summary summary(UUID id, UUID user) {
    var rows =
        jdbc.query(
            "SELECT r.user_id,u.first_name,r.status FROM events e JOIN communities c ON c.id=e.community_id AND c.bot_active JOIN community_memberships m ON m.community_id=e.community_id AND m.user_id=? AND m.active LEFT JOIN event_rsvps r ON r.event_id=e.id LEFT JOIN users u ON u.id=r.user_id WHERE e.id=? ORDER BY r.created_at,r.user_id",
            (r, n) -> new Participant(r.getObject(1, UUID.class), r.getString(2), r.getString(3)),
            user,
            id);
    if (rows.isEmpty()) events.get(id, user); // Preserve the normal 403/404 response.
    var participants = rows.stream().filter(p -> p.userId() != null).toList();
    return new Summary(
        count(participants, "GOING"),
        count(participants, "MAYBE"),
        count(participants, "WAITLISTED"),
        participants.stream()
            .filter(p -> p.userId.equals(user))
            .map(Participant::status)
            .findFirst()
            .orElse(null),
        participants);
  }

  private int count(List<Participant> list, String status) {
    return (int) list.stream().filter(p -> p.status.equals(status)).count();
  }

  @Transactional
  public void set(UUID id, UUID user, String requested) {
    if (requested == null || !Set.of("GOING", "MAYBE").contains(requested))
      throw new ApiException(400, "Choose Going or Maybe.");
    var e = events.locked(id, user);
    events.writable(e);
    var state =
        jdbc.queryForMap(
            "SELECT (SELECT status FROM event_rsvps WHERE event_id=? AND user_id=?) AS status, (SELECT count(*) FROM event_rsvps WHERE event_id=? AND status='GOING') AS going",
            id,
            user,
            id);
    String old = (String) state.get("status");
    if (requested.equals(old) || (requested.equals("GOING") && "WAITLISTED".equals(old))) return;
    String status =
        requested.equals("GOING")
                && e.capacity != null
                && ((Number) state.get("going")).intValue() >= e.capacity
            ? "WAITLISTED"
            : requested;
    jdbc.update(
        "INSERT INTO event_rsvps(event_id,user_id,status,created_at,updated_at,waitlisted_at) VALUES (?,?,?,now(),now(),CASE WHEN ?='WAITLISTED' THEN now() ELSE NULL END) ON CONFLICT(event_id,user_id) DO UPDATE SET status=EXCLUDED.status,updated_at=EXCLUDED.updated_at,waitlisted_at=EXCLUDED.waitlisted_at",
        id,
        user,
        status,
        status);
    if ("GOING".equals(old)) attendance.promote(e);
    events.changed(e, "RSVP_UPDATED");
  }

  @Transactional
  public void remove(UUID id, UUID user) {
    var e = events.locked(id, user);
    events.writable(e);
    if (jdbc.update("DELETE FROM event_rsvps WHERE event_id=? AND user_id=?", id, user) > 0) {
      attendance.promote(e);
      events.changed(e, "RSVP_UPDATED");
    }
  }
}
