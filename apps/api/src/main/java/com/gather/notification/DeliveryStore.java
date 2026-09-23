package com.gather.notification;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryStore {
  private final JdbcTemplate jdbc;

  public DeliveryStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Delivery(
      UUID id,
      UUID lease,
      UUID eventId,
      UUID userId,
      long telegramId,
      String type,
      String payload,
      int attempt) {}

  @Transactional
  public Delivery claim() {
    jdbc.update(
        "UPDATE notification_deliveries SET status='FAILED_FINAL',locked_until=NULL,lease_token=NULL WHERE status='IN_PROGRESS' AND locked_until<now() AND attempt_count>=5");
    var ids =
        jdbc.queryForList(
            "SELECT id FROM notification_deliveries WHERE attempt_count<5 AND ((status IN ('PENDING','RETRY') AND next_attempt_at<=now()) OR (status='IN_PROGRESS' AND locked_until<now())) ORDER BY next_attempt_at,created_at LIMIT 1 FOR UPDATE SKIP LOCKED",
            UUID.class);
    if (ids.isEmpty()) return null;
    var id = ids.getFirst();
    var lease = UUID.randomUUID();
    jdbc.update(
        "UPDATE notification_deliveries SET status='IN_PROGRESS',attempt_count=attempt_count+1,locked_until=now()+interval '30 seconds',lease_token=? WHERE id=?",
        lease,
        id);
    return jdbc.queryForObject(
        "SELECT d.*,o.aggregate_id,o.event_type,o.payload,u.telegram_user_id FROM notification_deliveries d JOIN outbox_events o ON o.id=d.outbox_event_id JOIN users u ON u.id=d.recipient_user_id WHERE d.id=?",
        (r, n) ->
            new Delivery(
                id,
                lease,
                r.getObject("aggregate_id", UUID.class),
                r.getObject("recipient_user_id", UUID.class),
                r.getLong("telegram_user_id"),
                r.getString("event_type"),
                r.getString("payload"),
                r.getInt("attempt_count")),
        id);
  }

  @Transactional
  public void finish(Delivery d, String status, Long messageId, int delay) {
    jdbc.update(
        "UPDATE notification_deliveries SET status=?,telegram_message_id=?,sent_at=CASE WHEN ?='SENT' THEN now() ELSE NULL END,next_attempt_at=now()+(? * interval '1 second'),locked_until=NULL,lease_token=NULL WHERE id=? AND lease_token=? AND status='IN_PROGRESS'",
        status,
        messageId,
        status,
        delay,
        d.id,
        d.lease);
  }

  @Transactional
  public void disableDm(UUID user) {
    jdbc.update("UPDATE users SET telegram_dm_enabled=FALSE WHERE id=?", user);
  }
}
