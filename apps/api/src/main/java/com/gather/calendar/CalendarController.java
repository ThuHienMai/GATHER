package com.gather.calendar;

import com.gather.event.EventService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events/{id}")
public class CalendarController {
  private final EventService events;
  private final CalendarService calendar;

  public CalendarController(EventService events, CalendarService calendar) {
    this.events = events;
    this.calendar = calendar;
  }

  @GetMapping(value = "/calendar.ics", produces = "text/calendar;charset=UTF-8")
  public ResponseEntity<String> ics(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"gather-" + id + ".ics\"")
        .header("Cache-Control", "private, no-store")
        .body(calendar.ics(events.get(id, UUID.fromString(jwt.getSubject()))));
  }

  @GetMapping("/calendar/google")
  public Map<String, String> google(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return Map.of("url", calendar.google(events.get(id, UUID.fromString(jwt.getSubject()))));
  }
}
