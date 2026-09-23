package com.gather.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.event.*;
import java.time.*;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class OutboxRecorder {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public OutboxRecorder(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void changed(EventService.Changed change) {
    insert(change.eventId(), change.type(), change.snapshot());
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void promoted(AttendanceOperations.Promoted change) {
    record(change.eventId(), "EVENT_WAITLIST_PROMOTED", change.userId());
  }

  public void record(UUID event, String type, UUID recipient) {
    var snapshot =
        jdbc.queryForMap(
            "SELECT title,community_id,start_at,end_at,location_text,status,version FROM events WHERE id=?",
            event);
    var payload = new LinkedHashMap<String, Object>();
    snapshot.forEach(
        (key, value) ->
            payload.put(
                key,
                value == null
                    ? null
                    : value instanceof java.sql.Timestamp timestamp
                        ? timestamp.toInstant().toString()
                        : value.toString()));
    if (recipient != null) payload.put("recipientUserId", recipient.toString());
    insert(event, type, payload);
  }

  private void insert(UUID event, String type, Map<String, Object> payload) {
    try {
      jdbc.update(
          "INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,payload,occurred_at) VALUES (?,'EVENT',?,?,?::jsonb,now())",
          UUID.randomUUID(),
          event,
          type,
          mapper.writeValueAsString(payload));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
