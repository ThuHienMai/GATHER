package com.gather.scheduling;

import static com.gather.scheduling.RangeAddSchedulingEngine.*;

import com.gather.event.Event;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events/{id}")
public class SchedulingController {
  private final SchedulingService service;

  public SchedulingController(SchedulingService service) {
    this.service = service;
  }

  public record Availability(List<Interval> intervals) {}

  public record Recommendations(List<Slot> slots) {}

  @GetMapping("/availability")
  public Availability mine(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return new Availability(service.mine(id, UUID.fromString(jwt.getSubject())));
  }

  @PutMapping("/availability")
  public Availability replace(
      @PathVariable UUID id, @RequestBody Availability input, @AuthenticationPrincipal Jwt jwt) {
    return new Availability(
        service.replace(id, UUID.fromString(jwt.getSubject()), input.intervals()));
  }

  @GetMapping("/schedule-recommendations")
  public Recommendations rank(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return new Recommendations(service.recommendations(id, UUID.fromString(jwt.getSubject())));
  }

  @PostMapping("/schedule/finalize")
  public ResponseEntity<Event> finalize(
      @PathVariable UUID id,
      @RequestHeader(value = "If-Match", required = false) String version,
      @RequestBody Interval slot,
      @AuthenticationPrincipal Jwt jwt) {
    var e = service.finalize(id, UUID.fromString(jwt.getSubject()), version, slot);
    return ResponseEntity.ok().eTag("\"" + e.version + "\"").body(e);
  }
}
