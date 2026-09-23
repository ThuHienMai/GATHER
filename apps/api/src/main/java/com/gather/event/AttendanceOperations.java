package com.gather.event;

import com.gather.common.ApiException;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AttendanceOperations {
  private final JdbcTemplate jdbc;
  private final ApplicationEventPublisher publisher;

  public AttendanceOperations(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
    this.jdbc = jdbc;
    this.publisher = publisher;
  }

  public int going(UUID event) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM event_rsvps WHERE event_id=? AND status='GOING'",
        Integer.class,
        event);
  }

  public void checkCapacity(Event e, Integer capacity) {
    if (capacity != null && capacity < going(e.id))
      throw new ApiException(409, "Capacity cannot be below the number already going.");
  }

  public void promote(Event e) {
    // The caller holds the event row lock. Select and promote in one round trip.
    var ids =
        jdbc.queryForList(
            "WITH waiting AS (SELECT user_id FROM event_rsvps WHERE event_id=? AND status='WAITLISTED' ORDER BY waitlisted_at,user_id LIMIT greatest(0,? - (SELECT count(*) FROM event_rsvps WHERE event_id=? AND status='GOING'))) UPDATE event_rsvps SET status='GOING',waitlisted_at=NULL,updated_at=now() WHERE event_id=? AND user_id IN (SELECT user_id FROM waiting) RETURNING user_id",
            UUID.class,
            e.id,
            e.capacity == null ? Integer.MAX_VALUE : e.capacity,
            e.id,
            e.id);
    for (var user : ids) {
      publisher.publishEvent(new Promoted(e.id, user));
    }
  }

  public record Promoted(UUID eventId, UUID userId) {}
}
