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

  @Test
  void setupJoinDeduplicateRevokeAndRollback() throws Exception {
    var setup =
        mapper.readTree(
            "{\"update_id\":1,\"message\":{\"date\":100,\"text\":\"/setup\",\"chat\":{\"id\":-100,\"type\":\"supergroup\",\"title\":\"Tokyo\"},\"from\":{\"id\":10}}}");
    assertThatThrownBy(() -> updates.process(setup, "MEMBER")).isInstanceOf(ApiException.class);
    assertThat(jdbc.queryForObject("select count(*) from telegram_updates", Integer.class))
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
}
