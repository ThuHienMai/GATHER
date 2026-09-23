package com.gather.notification;

import com.gather.common.ApiException;
import com.gather.event.EventService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {
  private final JdbcTemplate jdbc;
  private final EventService events;

  public NotificationController(JdbcTemplate jdbc, EventService events) {
    this.jdbc = jdbc;
    this.events = events;
  }

  public record Preference(String level) {}

  @PutMapping("/events/{id}/notification-preference")
  @Transactional
  public Preference set(
      @PathVariable UUID id, @RequestBody Preference input, @AuthenticationPrincipal Jwt jwt) {
    var user = UUID.fromString(jwt.getSubject());
    events.get(id, user);
    if (input.level() == null
        || !Set.of("ALL_ACTIVITY", "IMPORTANT_ONLY", "MUTED").contains(input.level()))
      throw new ApiException(400, "Invalid notification preference.");
    jdbc.update(
        "INSERT INTO notification_preferences VALUES (?,?,?) ON CONFLICT(event_id,user_id) DO UPDATE SET level=EXCLUDED.level",
        id,
        user,
        input.level());
    return input;
  }

  @GetMapping("/events/{id}/notification-preference")
  public Preference get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    var user = UUID.fromString(jwt.getSubject());
    var e = events.get(id, user);
    var explicit =
        jdbc.queryForList(
            "SELECT level FROM notification_preferences WHERE event_id=? AND user_id=?",
            String.class,
            id,
            user);
    if (!explicit.isEmpty()) return new Preference(explicit.getFirst());
    var rsvp =
        jdbc.queryForList(
            "SELECT status FROM event_rsvps WHERE event_id=? AND user_id=?",
            String.class,
            id,
            user);
    return new Preference(
        e.organizerId.equals(user)
                || rsvp.stream().anyMatch(s -> Set.of("GOING", "MAYBE").contains(s))
            ? "ALL_ACTIVITY"
            : rsvp.contains("WAITLISTED") ? "IMPORTANT_ONLY" : "MUTED");
  }

  @GetMapping("/me/notifications")
  public List<Map<String, Object>> list(
      @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page) {
    if (page < 0 || page > 100000) throw new ApiException(400, "Invalid page.");
    return jdbc.queryForList(
        "SELECT d.id,d.status,d.created_at,o.aggregate_id AS event_id,o.event_type,o.payload->>'title' AS title FROM notification_deliveries d JOIN outbox_events o ON o.id=d.outbox_event_id JOIN events e ON e.id=o.aggregate_id JOIN communities c ON c.id=e.community_id AND c.bot_active JOIN community_memberships m ON m.community_id=e.community_id AND m.user_id=d.recipient_user_id AND m.active WHERE d.recipient_user_id=? ORDER BY d.created_at DESC LIMIT 30 OFFSET ?",
        UUID.fromString(jwt.getSubject()),
        page * 30);
  }
}
