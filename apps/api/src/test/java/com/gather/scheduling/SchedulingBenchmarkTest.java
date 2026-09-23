package com.gather.scheduling;

import static com.gather.scheduling.RangeAddSchedulingEngine.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class SchedulingBenchmarkTest {
  static volatile Object sink;

  @Test
  @EnabledIfSystemProperty(named = "gather.benchmark", matches = "true")
  void benchmark() {
    var start = Instant.parse("2026-09-24T00:00:00Z");
    var end = start.plusSeconds(7 * 86400);
    var engine = new RangeAddSchedulingEngine();
    var bean =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    long thread = Thread.currentThread().threadId();
    for (int count : new int[] {100, 500, 1000, 5000}) {
      var people = new ArrayList<Attendee>();
      for (int i = 0; i < count; i++)
        people.add(
            new Attendee(
                i % 3 == 0 ? "MAYBE" : "GOING",
                List.of(
                    new Interval(
                        start.plusSeconds(i % 20 * 1800L), end.minusSeconds(i % 17 * 1800L)))));
      for (int i = 0; i < 100; i++) {
        sink = engine.rank(start, end, 120, people);
        sink = NaiveSchedulingEngine.rank(start, end, 120, people);
      }
      long allocation = bean.getThreadAllocatedBytes(thread), time = System.nanoTime();
      for (int i = 0; i < 200; i++) sink = engine.rank(start, end, 120, people);
      long optimized = System.nanoTime() - time,
          bytes = bean.getThreadAllocatedBytes(thread) - allocation;
      allocation = bean.getThreadAllocatedBytes(thread);
      time = System.nanoTime();
      for (int i = 0; i < 200; i++) sink = NaiveSchedulingEngine.rank(start, end, 120, people);
      long naive = System.nanoTime() - time,
          naiveBytes = bean.getThreadAllocatedBytes(thread) - allocation;
      System.out.printf(
          Locale.ROOT,
          "BENCH users=%d optimized_ms=%.4f naive_ms=%.4f optimized_bytes=%d naive_bytes=%d speedup=%.2f%n",
          count,
          optimized / 200e6,
          naive / 200e6,
          bytes / 200,
          naiveBytes / 200,
          (double) naive / optimized);
    }
  }
}
