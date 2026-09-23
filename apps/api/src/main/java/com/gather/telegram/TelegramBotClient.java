package com.gather.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TelegramBotClient {
  private final RestClient client;

  public TelegramBotClient(@Value("${gather.telegram.bot-token}") String token) {
    var factory =
        new JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(10));
    client =
        RestClient.builder()
            .baseUrl("https://api.telegram.org/bot" + token)
            .requestFactory(factory)
            .build();
  }

  public static class Failure extends RuntimeException {
    public final int code;
    public final int retryAfter;

    public Failure(int code, int retryAfter) {
      super("Telegram delivery failed");
      this.code = code;
      this.retryAfter = retryAfter;
    }
  }

  public JsonNode call(String method, Map<String, ?> body) {
    try {
      return client
          .post()
          .uri("/" + method)
          .body(body)
          .exchange(
              (request, response) -> {
                var json =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getBody());
                if (!response.getStatusCode().is2xxSuccessful()
                    || json == null
                    || !json.path("ok").asBoolean())
                  throw new Failure(
                      json == null
                          ? response.getStatusCode().value()
                          : json.path("error_code").asInt(response.getStatusCode().value()),
                      json == null ? 0 : json.path("parameters").path("retry_after").asInt());
                return json.path("result");
              });
    } catch (org.springframework.web.client.RestClientException e) {
      throw new Failure(503, 0);
    }
  }

  public String status(long chat, long user) {
    var member = call("getChatMember", Map.of("chat_id", chat, "user_id", user));
    String status = member.path("status").asText();
    return status.equals("restricted") && member.path("is_member").asBoolean() ? "member" : status;
  }

  public long botId() {
    return call("getMe", Map.of()).path("id").asLong();
  }
}
