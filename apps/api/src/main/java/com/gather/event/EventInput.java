package com.gather.event;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record EventInput(
    @NotBlank @Size(min = 3, max = 120) String title,
    @Size(max = 2000) String description,
    @Size(max = 250) String locationText,
    @Size(max = 500) String locationUrl,
    @NotNull String schedulingMode,
    Instant startAt,
    Instant endAt,
    Instant flexWindowStart,
    Instant flexWindowEnd,
    Integer durationMinutes,
    @NotBlank @Size(max = 64) String timezone,
    @Min(1) Integer capacity) {}
