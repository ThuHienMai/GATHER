package com.gather.scheduling;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RangeAddSchedulingEngine {
  public record Interval(Instant start, Instant end) {}

  public record Attendee(String status, List<Interval> intervals) {}

  public record Slot(
      Instant start, Instant end, int goingAvailable, int maybeAvailable, int score) {}

  public static final Comparator<Slot> RANK =
      Comparator.comparingInt(Slot::goingAvailable)
          .reversed()
          .thenComparing(Comparator.comparingInt(Slot::score).reversed())
          .thenComparing(Slot::start);

  public List<Slot> rank(
      Instant start, Instant end, int durationMinutes, List<Attendee> attendees) {
    long duration = durationMinutes * 60L, window = Duration.between(start, end).getSeconds();
    if (duration <= 0 || window < duration) return List.of();
    int count = (int) ((window - duration) / 1800) + 1;
    int[] going = new int[count + 1], maybe = new int[count + 1];
    for (var person : attendees) {
      if (!Set.of("GOING", "MAYBE").contains(person.status())) continue;
      int[] diff = person.status().equals("GOING") ? going : maybe;
      for (var interval : person.intervals()) {
        double from = Duration.between(start, interval.start()).toNanos() / 1_800_000_000_000.0;
        double to =
            Duration.between(start, interval.end().minusSeconds(duration)).toNanos()
                / 1_800_000_000_000.0;
        int first = Math.max(0, (int) Math.ceil(from)),
            last = Math.min(count - 1, (int) Math.floor(to));
        if (first <= last) {
          diff[first]++;
          diff[last + 1]--;
        }
      }
    }
    var best = new ArrayList<Slot>();
    int g = 0, m = 0;
    for (int i = 0; i < count; i++) {
      g += going[i];
      m += maybe[i];
      var time = start.plusSeconds(i * 1800L);
      best.add(new Slot(time, time.plusSeconds(duration), g, m, 2 * g + m));
      best.sort(RANK);
      if (best.size() > 3) best.removeLast();
    }
    return best;
  }

  public static List<Interval> normalize(List<Interval> input) {
    var sorted = new ArrayList<>(input);
    sorted.sort(Comparator.comparing(Interval::start));
    var merged = new ArrayList<Interval>();
    for (var interval : sorted) {
      if (merged.isEmpty() || merged.getLast().end().isBefore(interval.start()))
        merged.add(interval);
      else {
        var last = merged.removeLast();
        merged.add(
            new Interval(
                last.start(), last.end().isAfter(interval.end()) ? last.end() : interval.end()));
      }
    }
    return List.copyOf(merged);
  }
}
