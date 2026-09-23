package com.gather.event;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {
  @Id public UUID id;
  public UUID communityId;
  public UUID organizerId;
  public String title;
  public String description;
  public String locationText;
  public String locationUrl;

  @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"FIXED", "FLEXIBLE"})
  public String schedulingMode;

  public Instant startAt;
  public Instant endAt;
  public Instant flexWindowStart;
  public Instant flexWindowEnd;
  public Integer durationMinutes;
  public String timezone;
  public Integer capacity;

  @io.swagger.v3.oas.annotations.media.Schema(
      allowableValues = {"OPEN", "LOCKED", "CANCELLED", "COMPLETED"})
  public String status;

  @Version public long version;
  public Instant createdAt;
  public Instant updatedAt;
}
