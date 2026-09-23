package com.gather.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.gather.common.ApiException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private final UserRepository users;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public UserService(UserRepository users, JdbcTemplate jdbc, Clock clock) {
    this.users = users;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional
  public User upsert(JsonNode identity) {
    long telegramId = identity.path("id").asLong();
    var now = java.sql.Timestamp.from(clock.instant());
    jdbc.update(
        "INSERT INTO users(id,telegram_user_id,first_name,last_name,telegram_username,created_at,updated_at,last_active_at) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(telegram_user_id) DO UPDATE SET first_name=EXCLUDED.first_name,last_name=EXCLUDED.last_name,telegram_username=EXCLUDED.telegram_username,updated_at=EXCLUDED.updated_at,last_active_at=EXCLUDED.last_active_at",
        UUID.randomUUID(),
        telegramId,
        bounded(identity, "first_name", 128),
        bounded(identity, "last_name", 128),
        bounded(identity, "username", 64),
        now,
        now,
        now);
    var user = users.findByTelegramUserId(telegramId).orElseThrow();
    jdbc.update(
        "INSERT INTO user_activity_daily VALUES (?,?) ON CONFLICT DO NOTHING",
        user.id,
        java.time.LocalDate.now(clock));
    return user;
  }

  public User get(UUID id) {
    return users.findById(id).orElseThrow(() -> new ApiException(401, "User no longer exists."));
  }

  private String bounded(JsonNode node, String key, int max) {
    var s = node.path(key).asText("");
    return s.substring(0, Math.min(max, s.length()));
  }
}
