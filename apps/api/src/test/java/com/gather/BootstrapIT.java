package com.gather;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "gather.jwt-secret=test-only-secret-at-least-thirty-two-bytes-long",
      "gather.management-token=test-only-management-token-thirty-two-bytes"
    })
class BootstrapIT {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired TestRestTemplate http;
  @Autowired JdbcTemplate jdbc;

  @Test
  void readinessIncludesDatabaseAndMigration() {
    var response = http.getForEntity("/actuator/health/readiness", String.class);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).contains("UP");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class))
        .isPositive();
  }

  @Test
  void operationalMetricsAreNotPublic() {
    assertThat(
            http.getForEntity("/actuator/metrics", String.class).getStatusCode().is4xxClientError())
        .isTrue();
  }

  @Test
  void operationalMetricsRequireSeparateSecretAndApiErrorsAreProblems() {
    var headers = new org.springframework.http.HttpHeaders();
    headers.set("X-Management-Token", "test-only-management-token-thirty-two-bytes");
    var response =
        http.exchange(
            "/actuator/metrics",
            org.springframework.http.HttpMethod.GET,
            new org.springframework.http.HttpEntity<>(headers),
            String.class);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    var denied = http.getForEntity("/api/v1/me", String.class);
    assertThat(denied.getStatusCode().value()).isEqualTo(401);
    assertThat(denied.getHeaders().getContentType().toString())
        .contains("application/problem+json");
  }

  @Test
  void exportsOpenApi() throws Exception {
    var response = http.getForEntity("/api/openapi.json", String.class);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).contains("/api/v1/events/{id}");
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var document = mapper.readTree(response.getBody());
    ((com.fasterxml.jackson.databind.node.ObjectNode) document).remove("servers");
    java.nio.file.Files.writeString(
        java.nio.file.Path.of("target/openapi.json"),
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document));
  }
}
