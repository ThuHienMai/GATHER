package com.gather.event;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class EventController {
  private final EventService events;

  public EventController(EventService events) {
    this.events = events;
  }

  private UUID user(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }

  private ResponseEntity<Event> response(Event e) {
    return ResponseEntity.ok().eTag("\"" + e.version + "\"").body(e);
  }

  @GetMapping("/communities/{community}/events")
  public List<Event> list(
      @PathVariable UUID community,
      @RequestParam(defaultValue = "upcoming") String view,
      @RequestParam(defaultValue = "0") int page,
      @AuthenticationPrincipal Jwt jwt) {
    return events.list(community, user(jwt), view, page);
  }

  @PostMapping("/communities/{community}/events")
  public ResponseEntity<Event> create(
      @PathVariable UUID community,
      @Valid @RequestBody EventInput input,
      @AuthenticationPrincipal Jwt jwt) {
    return response(events.create(community, user(jwt), input));
  }

  @GetMapping("/events/{id}")
  public ResponseEntity<Event> get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return response(events.get(id, user(jwt)));
  }

  @PatchMapping("/events/{id}")
  public ResponseEntity<Event> edit(
      @PathVariable UUID id,
      @RequestHeader(value = "If-Match", required = false) String version,
      @Valid @RequestBody EventInput input,
      @AuthenticationPrincipal Jwt jwt) {
    return response(events.edit(id, user(jwt), version, input));
  }

  @PostMapping("/events/{id}/cancel")
  public ResponseEntity<Event> cancel(
      @PathVariable UUID id,
      @RequestHeader(value = "If-Match", required = false) String version,
      @AuthenticationPrincipal Jwt jwt) {
    return response(events.transition(id, user(jwt), version, "CANCELLED"));
  }

  @PostMapping("/events/{id}/lock")
  public ResponseEntity<Event> lock(
      @PathVariable UUID id,
      @RequestHeader(value = "If-Match", required = false) String version,
      @AuthenticationPrincipal Jwt jwt) {
    return response(events.transition(id, user(jwt), version, "LOCKED"));
  }
}
