package com.gather.notification;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationPolicy {
  public static final Set<String> IMPORTANT =
      Set.of(
          "EVENT_CANCELLED",
          "EVENT_TIME_CHANGED",
          "EVENT_LOCATION_CHANGED",
          "EVENT_WAITLIST_PROMOTED",
          "EVENT_STARTING_SOON");
  public static final Set<String> ACTIVITY = Set.of("COMMENT_CREATED");
  private final JdbcTemplate jdbc;

  public NotificationPolicy(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean eligible(UUID event, UUID user, String type) {
    var rows =
        jdbc.queryForList(
            "SELECT coalesce(p.level,CASE WHEN e.organizer_id=? OR r.status IN ('GOING','MAYBE') THEN 'ALL_ACTIVITY' WHEN r.status='WAITLISTED' THEN 'IMPORTANT_ONLY' ELSE 'MUTED' END) AS level FROM events e JOIN community_memberships m ON m.community_id=e.community_id AND m.user_id=? AND m.active JOIN communities c ON c.id=e.community_id AND c.bot_active JOIN users u ON u.id=m.user_id AND u.telegram_dm_enabled LEFT JOIN event_rsvps r ON r.event_id=e.id AND r.user_id=m.user_id LEFT JOIN notification_preferences p ON p.event_id=e.id AND p.user_id=m.user_id WHERE e.id=?",
            String.class,
            user,
            user,
            event);
    if (rows.isEmpty() || rows.getFirst().equals("MUTED")) return false;
    return IMPORTANT.contains(type)
        || (rows.getFirst().equals("ALL_ACTIVITY") && ACTIVITY.contains(type));
  }

  public List<UUID> recipients(UUID event, String type) {
    if (!IMPORTANT.contains(type) && !ACTIVITY.contains(type)) return List.of();
    return jdbc
        .queryForList(
            "SELECT user_id FROM community_memberships WHERE community_id=(SELECT community_id FROM events WHERE id=?) AND active",
            UUID.class,
            event)
        .stream()
        .filter(id -> eligible(event, id, type))
        .toList();
  }
}
