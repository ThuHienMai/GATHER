package com.gather.scheduling;

import static com.gather.scheduling.RangeAddSchedulingEngine.*;
import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SchedulingEngineTest {
  @Test
  void randomizedOracle() {
    var random = new Random(20260924);
    var start = Instant.parse("2026-11-01T04:00:00Z");
    var engine = new RangeAddSchedulingEngine();
    for (int trial = 0; trial < 4000; trial++) {
      int window = 1 + random.nextInt(100), duration = 30 + random.nextInt(451);
      var end = start.plusSeconds(window * 1800L);
      var attendees = new ArrayList<Attendee>();
      for (int u = 0; u < random.nextInt(30); u++) {
        var intervals = new ArrayList<Interval>();
        for (int j = 0; j < random.nextInt(12); j++) {
          int a = random.nextInt(window * 1800), b = a + 1 + random.nextInt(window * 1800 - a);
          intervals.add(new Interval(start.plusSeconds(a), start.plusSeconds(b)));
        }
        attendees.add(
            new Attendee(
                List.of("GOING", "MAYBE", "WAITLISTED").get(random.nextInt(3)),
                normalize(intervals)));
      }
      assertThat(engine.rank(start, end, duration, attendees))
          .isEqualTo(NaiveSchedulingEngine.rank(start, end, duration, attendees));
    }
  }

  @Test
  void mergesAdjacentIntervalsAndPreservesBoundary() {
    var start = Instant.EPOCH;
    var list =
        normalize(
            List.of(
                new Interval(start.plusSeconds(1800), start.plusSeconds(7200)),
                new Interval(start, start.plusSeconds(1800))));
    assertThat(list).containsExactly(new Interval(start, start.plusSeconds(7200)));
    var slots =
        new RangeAddSchedulingEngine()
            .rank(start, start.plusSeconds(7200), 120, List.of(new Attendee("GOING", list)));
    assertThat(slots).containsExactly(new Slot(start, start.plusSeconds(7200), 1, 0, 2));
  }
}
