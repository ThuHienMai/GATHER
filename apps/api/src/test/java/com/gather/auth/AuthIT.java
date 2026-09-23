package com.gather.auth;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "gather.jwt-secret=test-only-secret-at-least-thirty-two-bytes-long",
      "gather.telegram.bot-token=123:test-token"
    })
class AuthIT {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired TestRestTemplate http;
  @Autowired JdbcTemplate jdbc;

  @Test
  void authenticatesUpsertsAndAuthorizes() throws Exception {
    var raw =
        TelegramAuthServiceTest.signed(
            "123:test-token",
            Instant.now().getEpochSecond(),
            "{\"id\":42,\"first_name\":\"Alice\"}");
    var response =
        http.postForEntity(
            "/api/v1/auth/telegram", Map.of("initData", raw), AuthController.Session.class);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    var session = response.getBody();
    assertThat(session).isNotNull();
    var headers = new HttpHeaders();
    headers.setBearerAuth(session.token());
    var me = http.exchange("/api/v1/me", HttpMethod.GET, new HttpEntity<>(headers), User.class);
    assertThat(me.getBody().telegramUserId).isEqualTo(42);
    http.postForEntity(
        "/api/v1/auth/telegram", Map.of("initData", raw), AuthController.Session.class);
    assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from user_activity_daily", Integer.class))
        .isEqualTo(1);
    assertThat(http.getForEntity("/api/v1/me", String.class).getStatusCode().value())
        .isEqualTo(401);
    assertThat(
            http.postForEntity("/api/v1/auth/telegram", Map.of("initData", raw + "x"), String.class)
                .getStatusCode()
                .value())
        .isEqualTo(401);
  }

  @Test
  void allowsConfiguredBrowserPreflightAndRejectsOtherOrigins() {
    var headers = new HttpHeaders();
    headers.setOrigin("http://localhost:3000");
    headers.setAccessControlRequestMethod(HttpMethod.POST);
    headers.setAccessControlRequestHeaders(List.of("content-type"));
    var allowed =
        http.exchange(
            "/api/v1/auth/telegram", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
    assertThat(allowed.getStatusCode().value()).isEqualTo(200);
    assertThat(allowed.getHeaders().getAccessControlAllowOrigin())
        .isEqualTo("http://localhost:3000");
    headers.setOrigin("https://untrusted.example");
    assertThat(
            http.exchange(
                    "/api/v1/auth/telegram",
                    HttpMethod.OPTIONS,
                    new HttpEntity<>(headers),
                    String.class)
                .getStatusCode()
                .value())
        .isEqualTo(403);
  }
}
