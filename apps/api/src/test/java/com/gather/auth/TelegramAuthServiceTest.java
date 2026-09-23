package com.gather.auth;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.common.ApiException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class TelegramAuthServiceTest {
  static final String TOKEN = "123:test-token";
  static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");
  final TelegramAuthService service =
      new TelegramAuthService(TOKEN, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

  public static String signed(String token, long date, String user) throws Exception {
    var check = "auth_date=" + date + "\nuser=" + user;
    var key = TelegramAuthService.hmac("WebAppData".getBytes(StandardCharsets.UTF_8), token);
    var hash = HexFormat.of().formatHex(TelegramAuthService.hmac(key, check));
    return "user="
        + URLEncoder.encode(user, StandardCharsets.UTF_8)
        + "&auth_date="
        + date
        + "&hash="
        + hash;
  }

  @Test
  void acceptsSignedUser() throws Exception {
    assertThat(
            service
                .verify(signed(TOKEN, NOW.getEpochSecond(), "{\"id\":42,\"first_name\":\"A + B\"}"))
                .path("id")
                .asLong())
        .isEqualTo(42);
  }

  @Test
  void rejectsWrongToken() throws Exception {
    var raw = signed("forged", NOW.getEpochSecond(), "{\"id\":42}");
    assertThatThrownBy(() -> service.verify(raw)).isInstanceOf(ApiException.class);
  }

  @Test
  void rejectsExpiredAndFuture() throws Exception {
    for (long offset : new long[] {-301, 31}) {
      var raw = signed(TOKEN, NOW.getEpochSecond() + offset, "{\"id\":42}");
      assertThatThrownBy(() -> service.verify(raw)).isInstanceOf(ApiException.class);
    }
  }

  @Test
  void rejectsDuplicateMissingMalformedAndInvalidIdentity() throws Exception {
    var raw = signed(TOKEN, NOW.getEpochSecond(), "{\"id\":42}");
    for (var bad :
        new String[] {
          raw + "&user=x",
          raw.replace("hash=", "x="),
          "%broken",
          signed(TOKEN, NOW.getEpochSecond(), "{\"id\":-1}"),
          signed(TOKEN, NOW.getEpochSecond(), "{\"id\":1.5}")
        }) assertThatThrownBy(() -> service.verify(bad)).isInstanceOf(ApiException.class);
  }
}
