package com.gather.community;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.common.ApiException;
import com.gather.telegram.TelegramUpdateService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
@SpringBootTest(properties = "gather.jwt-secret=test-only-secret-at-least-thirty-two-bytes-long")
class CommunityIT {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired TelegramUpdateService updates;
  @Autowired CommunityService communities;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  com.gather.telegram.TelegramBotClient bot;

  @Test
  void setupJoinDeduplicateRevokeAndRollback() throws Exception {
    var setup =
        mapper.readTree(
            "{\"update_id\":1,\"message\":{\"date\":100,\"text\":\"/setup\",\"chat\":{\"id\":-100,\"type\":\"supergroup\",\"title\":\"Tokyo\"},\"from\":{\"id\":10}}}");
    assertThatThrownBy(() -> updates.process(setup, "MEMBER")).isInstanceOf(ApiException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from telegram_updates where update_id=1", Integer.class))
        .isZero();
    assertThat(updates.process(setup, "ADMIN")).isTrue();
    assertThat(updates.process(setup, "ADMIN")).isFalse();
    UUID user = jdbc.queryForObject("select id from users where telegram_user_id=10", UUID.class);
    UUID community = communities.list(user).getFirst().id();
    assertThat(communities.admin(community, user)).isTrue();
    assertThat(communities.get(community, user).role()).isEqualTo("ADMIN");
    assertThatThrownBy(() -> communities.requireMember(community, UUID.randomUUID()))
        .isInstanceOf(ApiException.class);
    var leave =
        mapper.readTree(
            "{\"update_id\":2,\"chat_member\":{\"date\":200,\"chat\":{\"id\":-100},\"new_chat_member\":{\"status\":\"left\",\"user\":{\"id\":10}}}}");
    updates.process(leave, "MEMBER");
    assertThat(communities.list(user)).isEmpty();
    assertThatThrownBy(() -> communities.requireMember(community, user))
        .isInstanceOf(ApiException.class);
    updates.process(
        mapper.readTree(
            leave
                .toString()
                .replace("\"update_id\":2", "\"update_id\":3")
                .replace("\"left\"", "\"member\"")
                .replace("200", "300")),
        "MEMBER");
    assertThat(communities.list(user)).isEmpty();
    // Same-second messages can arrive out of order: the later removal wins.
    updates.process(
        mapper.readTree(
            leave.toString().replace("\"update_id\":2", "\"update_id\":5").replace("200", "300")),
        "MEMBER");
    updates.process(
        mapper.readTree(
            setup
                .toString()
                .replace("\"update_id\":1", "\"update_id\":4")
                .replace("\"date\":100", "\"date\":300")
                .replace("/setup", "/join")),
        "MEMBER");
    assertThat(communities.list(user)).isEmpty();
    updates.process(
        mapper.readTree(
            setup
                .toString()
                .replace("\"update_id\":1", "\"update_id\":6")
                .replace("\"date\":100", "\"date\":300")
                .replace("/setup", "/join")),
        "MEMBER");
    assertThat(communities.get(community, user).role()).isEqualTo("MEMBER");
  }

  @Test
  void rejectedCommandsAreAcknowledgedOnceAndFreshSetupStillWorks() throws Exception {
    var controller =
        new com.gather.telegram.TelegramWebhookController(
            "test-webhook", mapper, bot, updates, null);
    org.mockito.Mockito.when(bot.status(-200, 20)).thenReturn("creator");
    org.mockito.Mockito.when(bot.botId()).thenReturn(99L);
    org.mockito.Mockito.when(bot.status(-200, 99)).thenReturn("administrator");
    var join =
        "{\"update_id\":100,\"message\":{\"date\":100,\"text\":\"/join\",\"chat\":{\"id\":-200,\"type\":\"supergroup\",\"title\":\"Test\"},\"from\":{\"id\":20}}}";
    var bytes = join.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(controller.receive("test-webhook", bytes))
        .containsEntry("method", "sendMessage")
        .containsEntry("text", "Ask a group administrator to run /setup first.");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from users where telegram_user_id=20", Integer.class))
        .isZero();
    assertThat(controller.receive("test-webhook", bytes))
        .containsEntry("ok", true)
        .doesNotContainKey("method");
    var setup =
        join.replace("100,\"message", "101,\"message")
            .replace("/join", "/setup@gather_minerva_bot");
    assertThat(
            controller.receive(
                "test-webhook", setup.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        .containsEntry("ok", true);
    var user = jdbc.queryForObject("select id from users where telegram_user_id=20", UUID.class);
    assertThat(communities.list(user)).hasSize(1);
    assertThatThrownBy(() -> controller.receive("wrong-secret", bytes))
        .isInstanceOf(ApiException.class);
    var retryable = join.replace("100,\"message", "102,\"message");
    org.mockito.Mockito.when(bot.status(-200, 20))
        .thenThrow(new com.gather.telegram.TelegramBotClient.Failure(503, 0));
    assertThatThrownBy(
            () ->
                controller.receive(
                    "test-webhook", retryable.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        .isInstanceOf(com.gather.telegram.TelegramBotClient.Failure.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from telegram_updates where update_id=102", Integer.class))
        .isZero();
  }
}
