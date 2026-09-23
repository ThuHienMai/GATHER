package com.gather.auth;

import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {
  private final UserService users;

  public SettingsService(UserService users) {
    this.users = users;
  }

  @Transactional
  public User timezone(UUID id, String timezone) {
    ZoneId.of(timezone);
    var user = users.get(id);
    user.timezone = timezone;
    return user;
  }
}
