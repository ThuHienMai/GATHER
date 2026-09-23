package com.gather.scheduling;

import static com.gather.scheduling.RangeAddSchedulingEngine.*;

import java.time.*;
import java.util.*;

class NaiveSchedulingEngine {
  static List<Slot> rank(Instant start, Instant end, int minutes, List<Attendee> attendees) {
    var result = new ArrayList<Slot>();
    for (var time = start;
        !time.plusSeconds(minutes * 60L).isAfter(end);
        time = time.plusSeconds(1800)) {
      int going = 0, maybe = 0;
      for (var person : attendees) {
        boolean available = false;
        for (var i : person.intervals())
          if (!i.start().isAfter(time) && !i.end().isBefore(time.plusSeconds(minutes * 60L))) {
            available = true;
            break;
          }
        if (available) {
          if (person.status().equals("GOING")) going++;
          if (person.status().equals("MAYBE")) maybe++;
        }
      }
      result.add(new Slot(time, time.plusSeconds(minutes * 60L), going, maybe, 2 * going + maybe));
    }
    result.sort(RANK);
    return result.stream().limit(3).toList();
  }
}
