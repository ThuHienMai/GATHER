package com.gather.calendar;

import com.gather.common.ApiException;
import com.gather.event.Event;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CalendarService {
  private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  public String ics(Event e) {
    scheduled(e);
    var lines =
        List.of(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Gather//Campus Events//EN",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "BEGIN:VEVENT",
            "UID:" + e.id + "@gather",
            "DTSTAMP:" + FORMAT.format(e.updatedAt),
            "SEQUENCE:" + e.version,
            "DTSTART:" + FORMAT.format(e.startAt),
            "DTEND:" + FORMAT.format(e.endAt),
            "SUMMARY:" + escape(e.title),
            "DESCRIPTION:" + escape(Objects.toString(e.description, "")),
            "LOCATION:" + escape(Objects.toString(e.locationText, "")),
            "STATUS:" + (e.status.equals("CANCELLED") ? "CANCELLED" : "CONFIRMED"),
            "END:VEVENT",
            "END:VCALENDAR");
    return lines.stream()
            .map(CalendarService::fold)
            .collect(java.util.stream.Collectors.joining("\r\n"))
        + "\r\n";
  }

  public String google(Event e) {
    scheduled(e);
    if (e.status.equals("CANCELLED")) throw new ApiException(409, "This event was cancelled.");
    return "https://calendar.google.com/calendar/render?action=TEMPLATE&text="
        + encode(e.title)
        + "&dates="
        + FORMAT.format(e.startAt)
        + "/"
        + FORMAT.format(e.endAt)
        + "&details="
        + encode(Objects.toString(e.description, ""))
        + "&location="
        + encode(Objects.toString(e.locationText, ""))
        + "&ctz="
        + encode(e.timezone);
  }

  private void scheduled(Event e) {
    if (e.startAt == null || e.endAt == null)
      throw new ApiException(409, "Finalize a time before adding this event to a calendar.");
  }

  static String escape(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace("\n", "\\n")
        .replace(";", "\\;")
        .replace(",", "\\,");
  }

  static String fold(String line) {
    var out = new StringBuilder();
    int bytes = 0;
    for (int i = 0; i < line.length(); ) {
      int cp = line.codePointAt(i);
      String text = new String(Character.toChars(cp));
      int size = text.getBytes(StandardCharsets.UTF_8).length;
      if (bytes + size > 75) {
        out.append("\r\n ");
        bytes = 1;
      }
      out.append(text);
      bytes += size;
      i += Character.charCount(cp);
    }
    return out.toString();
  }

  private String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
