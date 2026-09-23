package com.gather.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.realtime.GatherWebSocketHandler;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxDispatcher {
  private final JdbcTemplate jdbc;
  private final GatherWebSocketHandler sockets;
  private final NotificationPolicy policy;
  private final ObjectMapper mapper;

  public OutboxDispatcher(
      JdbcTemplate jdbc,
      GatherWebSocketHandler sockets,
      NotificationPolicy policy,
      ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.sockets = sockets;
    this.policy = policy;
    this.mapper = mapper;
  }

  @Scheduled(
      initialDelayString = "${gather.worker-initial-delay:0}",
      fixedDelayString = "${gather.outbox-delay:25}")
  @Transactional
  public void dispatch() throws Exception {
    var rows =
        jdbc.queryForList(
            "SELECT * FROM outbox_events WHERE processed_at IS NULL ORDER BY occurred_at,id LIMIT 200 FOR UPDATE SKIP LOCKED");
    for (var row : rows) {
      var event = (UUID) row.get("aggregate_id");
      var id = (UUID) row.get("id");
      var type = (String) row.get("event_type");
      var payload = mapper.readTree(row.get("payload").toString());
      for (var recipient : policy.recipients(event, type)) {
        if (payload.has("recipientUserId")
            && !payload.path("recipientUserId").asText().equals(recipient.toString())) continue;
        jdbc.update(
            "INSERT INTO notification_deliveries(id,outbox_event_id,recipient_user_id,status,created_at) VALUES (?,?,?,'PENDING',now()) ON CONFLICT(outbox_event_id,recipient_user_id,channel) DO NOTHING",
            UUID.randomUUID(),
            id,
            recipient);
      }
      sockets.broadcast(event, type, id, ((java.sql.Timestamp) row.get("occurred_at")).toInstant());
      jdbc.update("UPDATE outbox_events SET processed_at=now() WHERE id=?", id);
    }
  }
}
