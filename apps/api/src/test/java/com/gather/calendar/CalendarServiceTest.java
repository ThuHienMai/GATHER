package com.gather.calendar;

import static org.assertj.core.api.Assertions.*;

import com.gather.event.Event;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CalendarServiceTest {
  @Test
  void exportsStableUtcEscapedFoldedCalendar() {
    var e = new Event();
    e.id = UUID.randomUUID();
    e.title = "Dinner, friends; 東京".repeat(8);
    e.description = "line1\r\nline2\\tail";
    e.startAt = Instant.parse("2026-09-25T10:00:00Z");
    e.endAt = e.startAt.plusSeconds(7200);
    e.updatedAt = Instant.parse("2026-09-24T00:00:00Z");
    e.status = "CANCELLED";
    e.version = 4;
    e.timezone = "Asia/Tokyo";
    var service = new CalendarService();
    var text = service.ics(e);
    assertThat(text)
        .contains(
            "UID:" + e.id + "@gather",
            "DTSTART:20260925T100000Z",
            "STATUS:CANCELLED",
            "SEQUENCE:4",
            "DESCRIPTION:line1\\nline2\\\\tail");
    for (var line : text.split("\r\n"))
      assertThat(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
          .isLessThanOrEqualTo(75);
    assertThat(service.ics(e)).isEqualTo(text);
    e.status = "LOCKED";
    assertThat(service.google(e))
        .contains("ctz=Asia%2FTokyo", "dates=20260925T100000Z/20260925T120000Z");
  }
}
