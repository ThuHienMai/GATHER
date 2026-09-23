package com.gather.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.common.ApiException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TelegramAuthService {
  private final String botToken;
  private final ObjectMapper mapper;
  private final Clock clock;

  public TelegramAuthService(
      @Value("${gather.telegram.bot-token}") String botToken, ObjectMapper mapper, Clock clock) {
    this.botToken = botToken;
    this.mapper = mapper;
    this.clock = clock;
  }

  public JsonNode verify(String raw) {
    if (botToken.isBlank())
      throw new ApiException(503, "Telegram authentication is not configured.");
    try {
      if (raw == null || raw.isBlank() || raw.length() > 16384)
        throw new IllegalArgumentException();
      var fields = new TreeMap<String, String>();
      for (var part : raw.split("&")) {
        var pair = part.split("=", 2);
        if (pair.length != 2 || fields.putIfAbsent(decode(pair[0]), decode(pair[1])) != null)
          throw new IllegalArgumentException();
      }
      var hash = HexFormat.of().parseHex(fields.remove("hash"));
      var check =
          fields.entrySet().stream()
              .map(e -> e.getKey() + "=" + e.getValue())
              .collect(Collectors.joining("\n"));
      var key = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), botToken);
      if (!MessageDigest.isEqual(hash, hmac(key, check))) throw new IllegalArgumentException();
      long age = clock.instant().getEpochSecond() - Long.parseLong(fields.get("auth_date"));
      if (age > 300 || age < -30) throw new IllegalArgumentException();
      var user = mapper.readTree(fields.get("user"));
      if (!user.path("id").isIntegralNumber()
          || !user.path("id").canConvertToLong()
          || user.path("id").asLong() <= 0
          || user.path("is_bot").asBoolean()) throw new IllegalArgumentException();
      return user;
    } catch (Exception e) {
      throw new ApiException(401, "Invalid or expired Telegram launch. Reopen Gather in Telegram.");
    }
  }

  private static String decode(String s) {
    return URLDecoder.decode(s, StandardCharsets.UTF_8);
  }

  static byte[] hmac(byte[] key, String value) throws Exception {
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
  }
}
