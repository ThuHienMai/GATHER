package com.gather.event;

import com.gather.telegram.TelegramUpdateService;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MembershipAttendanceListener {
  private final JdbcTemplate jdbc;
  private final EventRepository events;
  private final EventService service;
  private final AttendanceOperations attendance;

  public MembershipAttendanceListener(
      JdbcTemplate jdbc,
      EventRepository events,
      EventService service,
      AttendanceOperations attendance) {
    this.jdbc = jdbc;
    this.events = events;
    this.service = service;
    this.attendance = attendance;
  }

  @EventListener
  public void revoke(TelegramUpdateService.MembershipChanged change) {
    if (Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT active FROM community_memberships WHERE community_id=? AND user_id=?",
            Boolean.class,
            change.communityId(),
            change.userId()))) return;
    var ids =
        jdbc.queryForList(
            "SELECT e.id FROM events e JOIN event_rsvps r ON r.event_id=e.id WHERE e.community_id=? AND r.user_id=? AND e.status IN ('OPEN','LOCKED') AND coalesce(e.end_at,e.flex_window_end)>now() ORDER BY e.id",
            UUID.class,
            change.communityId(),
            change.userId());
    for (var id : ids) {
      var e = events.lock(id).orElseThrow();
      jdbc.update("DELETE FROM event_rsvps WHERE event_id=? AND user_id=?", id, change.userId());
      jdbc.update(
          "DELETE FROM availability_intervals WHERE event_id=? AND user_id=?", id, change.userId());
      attendance.promote(e);
      service.changed(e, "RSVP_UPDATED");
    }
  }
}
