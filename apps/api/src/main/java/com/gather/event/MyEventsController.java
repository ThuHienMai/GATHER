package com.gather.event;

import com.gather.common.ApiException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class MyEventsController {
  private final JdbcTemplate jdbc;
  private final EventRepository events;

  public MyEventsController(JdbcTemplate jdbc, EventRepository events) {
    this.jdbc = jdbc;
    this.events = events;
  }

  @GetMapping("/api/v1/me/events")
  public List<Event> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "upcoming") String view,
      @RequestParam(defaultValue = "0") int page) {
    if (page < 0 || page > 100000) throw new ApiException(400, "Invalid page.");
    var user = UUID.fromString(jwt.getSubject());
    var condition =
        view.equals("past")
            ? "(e.status IN ('CANCELLED','COMPLETED') OR coalesce(e.end_at,e.flex_window_end)<=now())"
            : "e.status IN ('OPEN','LOCKED') AND coalesce(e.end_at,e.flex_window_end)>now()";
    var ids =
        jdbc.queryForList(
            "SELECT e.id FROM events e JOIN community_memberships m ON m.community_id=e.community_id AND m.user_id=? AND m.active JOIN communities c ON c.id=e.community_id AND c.bot_active WHERE (e.organizer_id=? OR EXISTS(SELECT 1 FROM event_rsvps r WHERE r.event_id=e.id AND r.user_id=?)) AND "
                + condition
                + " ORDER BY coalesce(e.start_at,e.flex_window_start),e.id LIMIT 30 OFFSET ?",
            UUID.class,
            user,
            user,
            user,
            page * 30);
    var result = events.findAllById(ids);
    result.sort(Comparator.comparingInt(e -> ids.indexOf(e.id)));
    return result;
  }
}
