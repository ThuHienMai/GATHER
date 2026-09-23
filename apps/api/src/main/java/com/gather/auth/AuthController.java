package com.gather.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
  private final TelegramAuthService telegram;
  private final UserService users;
  private final JwtEncoder encoder;
  private final Clock clock;

  public AuthController(
      TelegramAuthService telegram, UserService users, JwtEncoder encoder, Clock clock) {
    this.telegram = telegram;
    this.users = users;
    this.encoder = encoder;
    this.clock = clock;
  }

  public record Login(@NotBlank @Size(max = 16384) String initData) {}

  public record Session(String token, Instant expiresAt, User user) {}

  @PostMapping("/auth/telegram")
  public Session login(@Valid @RequestBody Login request) {
    var user = users.upsert(telegram.verify(request.initData()));
    var now = clock.instant();
    var expiry = now.plusSeconds(3600);
    var claims =
        JwtClaimsSet.builder()
            .subject(user.id.toString())
            .issuedAt(now)
            .expiresAt(expiry)
            .claim("telegramUserId", user.telegramUserId)
            .build();
    var token =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new Session(token, expiry, user);
  }

  @GetMapping("/me")
  public User me(@AuthenticationPrincipal Jwt jwt) {
    return users.get(UUID.fromString(jwt.getSubject()));
  }
}
