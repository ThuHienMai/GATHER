package com.gather.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class SettingsController {
  private final SettingsService settings;

  public SettingsController(SettingsService settings) {
    this.settings = settings;
  }

  public record SettingsInput(@NotBlank @Size(max = 64) String timezone) {}

  @PatchMapping("/api/v1/me")
  public User update(@Valid @RequestBody SettingsInput input, @AuthenticationPrincipal Jwt jwt) {
    return settings.timezone(UUID.fromString(jwt.getSubject()), input.timezone());
  }
}
