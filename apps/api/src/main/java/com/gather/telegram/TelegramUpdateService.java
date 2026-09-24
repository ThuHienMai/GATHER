package com.gather.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.gather.auth.UserService;
import com.gather.common.ApiException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelegramUpdateService {
  public record MembershipChanged(UUID communityId, UUID userId) {}

  private final JdbcTemplate jdbc;
  private final UserService users;
  private final Clock clock;
  private final ApplicationEventPublisher publisher;

  public TelegramUpdateService(
      JdbcTemplate jdbc, UserService users, Clock clock, ApplicationEventPublisher publisher) {
    this.jdbc = jdbc;
    this.users = users;
    this.clock = clock;
    this.publisher = publisher;
  }

  @Transactional
  public boolean acknowledgeRejected(long updateId) {
    var now = Timestamp.from(clock.instant());
    return jdbc.update(
            "INSERT INTO telegram_updates(update_id,received_at,processed_at) VALUES (?,?,?) ON CONFLICT DO NOTHING",
            updateId,
            now,
            now)
        == 1;
  }

  @Transactional
  public boolean process(JsonNode update, String verifiedRole) {
    if (!update.path("update_id").isIntegralNumber())
      throw new ApiException(400, "Missing update ID.");
    var now = Timestamp.from(clock.instant());
    long updateId = update.path("update_id").asLong();
    if (jdbc.update(
            "INSERT INTO telegram_updates(update_id,received_at) VALUES (?,?) ON CONFLICT DO NOTHING",
            updateId,
            now)
        == 0) return false;
    var message = update.path("message");
    var membership = update.path("chat_member");
    var botMembership = update.path("my_chat_member");
    if (!message.isMissingNode()
        && message.path("from").path("id").isIntegralNumber()
        && !message.path("from").path("is_bot").asBoolean()) {
      var command = message.path("text").asText("").split("[ @]", 2)[0];
      long chat = message.path("chat").path("id").asLong();
      if (command.equals("/setup") || command.equals("/join")) {
        if (!Set.of("group", "supergroup").contains(message.path("chat").path("type").asText()))
          throw new ApiException(400, "Run this command inside your Telegram group.");
        var user = users.upsert(message.path("from"));
        if (command.equals("/setup")) {
          if (!"ADMIN".equals(verifiedRole))
            throw new ApiException(403, "Only a Telegram administrator can set up Gather.");
          var name = message.path("chat").path("title").asText("Community");
          if (name.length() > 150) name = name.substring(0, 150);
          jdbc.update(
              "INSERT INTO communities(id,name,slug,telegram_chat_id,timezone,created_at) VALUES (?,?,?,?,?,?) ON CONFLICT(telegram_chat_id) DO UPDATE SET bot_active=TRUE",
              UUID.randomUUID(),
              name,
              "telegram-" + chat,
              chat,
              "Asia/Tokyo",
              now);
        }
        var ids =
            jdbc.queryForList(
                "SELECT id FROM communities WHERE telegram_chat_id=? AND bot_active",
                UUID.class,
                chat);
        if (ids.isEmpty())
          throw new ApiException(400, "Ask a group administrator to run /setup first.");
        var occurred =
            Timestamp.from(
                Instant.ofEpochSecond(
                    message.path("date").asLong(clock.instant().getEpochSecond())));
        jdbc.update(
            "INSERT INTO community_memberships(community_id,user_id,role,joined_at,telegram_updated_at,telegram_update_id) VALUES (?,?,?,?,?,?) ON CONFLICT(community_id,user_id) DO UPDATE SET active=TRUE,role=EXCLUDED.role,telegram_updated_at=EXCLUDED.telegram_updated_at,telegram_update_id=EXCLUDED.telegram_update_id WHERE (community_memberships.telegram_updated_at,community_memberships.telegram_update_id)<=(EXCLUDED.telegram_updated_at,EXCLUDED.telegram_update_id)",
            ids.getFirst(),
            user.id,
            verifiedRole,
            now,
            occurred,
            updateId);
      } else if (command.equals("/start")
          && message.path("chat").path("type").asText().equals("private")) {
        var user = users.upsert(message.path("from"));
        jdbc.update("UPDATE users SET telegram_dm_enabled=TRUE WHERE id=?", user.id);
      }
    } else if (!membership.isMissingNode()) {
      var member = membership.path("new_chat_member");
      long chat = membership.path("chat").path("id").asLong();
      long telegramUser = member.path("user").path("id").asLong();
      var status = member.path("status").asText();
      boolean present =
          Set.of("creator", "administrator", "member").contains(status)
              || (status.equals("restricted") && member.path("is_member").asBoolean());
      var occurred = Timestamp.from(Instant.ofEpochSecond(membership.path("date").asLong()));
      var rows =
          jdbc.queryForList(
              "SELECT m.community_id,m.user_id FROM community_memberships m JOIN users u ON u.id=m.user_id JOIN communities c ON c.id=m.community_id WHERE c.telegram_chat_id=? AND u.telegram_user_id=?",
              chat,
              telegramUser);
      for (var row : rows) {
        jdbc.update(
            "UPDATE community_memberships SET active=active AND ?,role=?,telegram_updated_at=?,telegram_update_id=? WHERE community_id=? AND user_id=? AND (telegram_updated_at,telegram_update_id)<=(?,?)",
            present,
            isAdmin(status) ? "ADMIN" : "MEMBER",
            occurred,
            updateId,
            row.get("community_id"),
            row.get("user_id"),
            occurred,
            updateId);
        publisher.publishEvent(
            new MembershipChanged((UUID) row.get("community_id"), (UUID) row.get("user_id")));
      }
    } else if (!botMembership.isMissingNode()) {
      long chat = botMembership.path("chat").path("id").asLong();
      boolean active = isAdmin(botMembership.path("new_chat_member").path("status").asText());
      var occurred = Timestamp.from(Instant.ofEpochSecond(botMembership.path("date").asLong()));
      jdbc.update(
          "UPDATE communities SET bot_active=?,telegram_updated_at=?,telegram_update_id=? WHERE telegram_chat_id=? AND (telegram_updated_at,telegram_update_id)<=(?,?)",
          active,
          occurred,
          updateId,
          chat,
          occurred,
          updateId);
    }
    jdbc.update("UPDATE telegram_updates SET processed_at=? WHERE update_id=?", now, updateId);
    return true;
  }

  public static boolean isAdmin(String status) {
    return status.equals("creator") || status.equals("administrator");
  }
}
