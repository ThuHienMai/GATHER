package com.gather.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
  @Id public UUID id;

  @Column(unique = true, nullable = false)
  public long telegramUserId;

  public String telegramUsername;
  public String firstName;
  public String lastName;
  public String timezone;
  public Instant createdAt;
  public Instant updatedAt;
  public Instant lastActiveAt;
  public boolean telegramDmEnabled;
}
