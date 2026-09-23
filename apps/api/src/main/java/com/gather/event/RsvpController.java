package com.gather.event;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events/{id}")
public class RsvpController {
  private final RsvpService service;
  // Bound admission for hot events before opening a transaction. Otherwise waiting
  // writers occupy every JDBC connection and starve the post-commit reads.
  // PostgreSQL row locks remain the correctness mechanism, including other writers.
  private final java.util.concurrent.locks.ReentrantLock[] admission =
      java.util.stream.IntStream.range(0, 256)
          .mapToObj(i -> new java.util.concurrent.locks.ReentrantLock(true))
          .toArray(java.util.concurrent.locks.ReentrantLock[]::new);

  private java.util.concurrent.locks.ReentrantLock gate(UUID id) {
    return admission[Math.floorMod(id.hashCode(), admission.length)];
  }

  public RsvpController(RsvpService service) {
    this.service = service;
  }

  public record Choice(String status) {}

  @GetMapping("/rsvps")
  public RsvpService.Summary get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return service.summary(id, UUID.fromString(jwt.getSubject()));
  }

  @PutMapping("/rsvp")
  public RsvpService.Summary set(
      @PathVariable UUID id, @RequestBody Choice choice, @AuthenticationPrincipal Jwt jwt) {
    var user = UUID.fromString(jwt.getSubject());
    var gate = gate(id);
    gate.lock();
    try {
      service.set(id, user, choice.status());
    } finally {
      gate.unlock();
    }
    return service.summary(id, user);
  }

  @DeleteMapping("/rsvp")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void remove(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    var gate = gate(id);
    gate.lock();
    try {
      service.remove(id, UUID.fromString(jwt.getSubject()));
    } finally {
      gate.unlock();
    }
  }
}
