package com.gather.scheduling;

import static com.gather.scheduling.RangeAddSchedulingEngine.*;

import com.gather.common.ApiException;
import com.gather.event.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulingService {
  private final EventService events;
  private final EventRepository repository;
  private final JdbcTemplate jdbc;
  private final RangeAddSchedulingEngine engine;
  private final Clock clock;
  private final io.micrometer.core.instrument.Timer scoringTimer;

  public SchedulingService(
      EventService events,
      EventRepository repository,
      JdbcTemplate jdbc,
      RangeAddSchedulingEngine engine,
      Clock clock,
      io.micrometer.core.instrument.MeterRegistry registry) {
    this.events = events;
    this.repository = repository;
    this.jdbc = jdbc;
    this.engine = engine;
    this.clock = clock;
    this.scoringTimer = registry.timer("gather.scheduling.scoring");
  }

  public List<Interval> mine(UUID id, UUID user) {
    events.get(id, user);
    return jdbc.query(
        "SELECT start_at,end_at FROM availability_intervals WHERE event_id=? AND user_id=? ORDER BY start_at",
        (r, n) -> new Interval(r.getTimestamp(1).toInstant(), r.getTimestamp(2).toInstant()),
        id,
        user);
  }

  @Transactional
  public List<Interval> replace(UUID id, UUID user, List<Interval> intervals) {
    var e = events.locked(id, user);
    open(e);
    if (intervals == null || intervals.size() > 1000)
      throw new ApiException(400, "Too many availability intervals.");
    for (var i : intervals)
      if (i == null
          || i.start() == null
          || i.end() == null
          || !i.end().isAfter(i.start())
          || i.start().isBefore(e.flexWindowStart)
          || i.end().isAfter(e.flexWindowEnd))
        throw new ApiException(400, "Availability must be within the planning window.");
    var merged = normalize(intervals);
    jdbc.update("DELETE FROM availability_intervals WHERE event_id=? AND user_id=?", id, user);
    for (var i : merged)
      jdbc.update(
          "INSERT INTO availability_intervals VALUES (?,?,?,?,?,now())",
          UUID.randomUUID(),
          id,
          user,
          Timestamp.from(i.start()),
          Timestamp.from(i.end()));
    events.changed(e, "AVAILABILITY_UPDATED");
    return merged;
  }

  public List<Slot> recommendations(UUID id, UUID user) {
    var e = events.get(id, user);
    if (!e.schedulingMode.equals("FLEXIBLE"))
      throw new ApiException(409, "This event has a fixed schedule.");
    var intervals = new LinkedHashMap<UUID, List<Interval>>();
    var statuses = new HashMap<UUID, String>();
    jdbc.query(
        "SELECT a.user_id,a.start_at,a.end_at,r.status FROM availability_intervals a JOIN event_rsvps r ON r.event_id=a.event_id AND r.user_id=a.user_id JOIN community_memberships m ON m.user_id=a.user_id AND m.community_id=? WHERE a.event_id=? AND r.status IN ('GOING','MAYBE') AND m.active ORDER BY a.user_id,a.start_at",
        r -> {
          var person = r.getObject(1, UUID.class);
          intervals
              .computeIfAbsent(person, k -> new ArrayList<>())
              .add(new Interval(r.getTimestamp(2).toInstant(), r.getTimestamp(3).toInstant()));
          statuses.put(person, r.getString(4));
        },
        e.communityId,
        id);
    return scoringTimer.record(
        () ->
            engine.rank(
                e.flexWindowStart,
                e.flexWindowEnd,
                e.durationMinutes,
                intervals.entrySet().stream()
                    .map(entry -> new Attendee(statuses.get(entry.getKey()), entry.getValue()))
                    .toList()));
  }

  @Transactional
  public Event finalize(UUID id, UUID user, String version, Interval chosen) {
    var e = events.locked(id, user);
    events.organizer(e, user);
    events.match(e, version);
    open(e);
    if (chosen == null
        || chosen.start() == null
        || chosen.end() == null
        || chosen.start().isBefore(e.flexWindowStart)
        || chosen.end().isAfter(e.flexWindowEnd)
        || !chosen.end().equals(chosen.start().plusSeconds(e.durationMinutes * 60L))
        || Duration.between(e.flexWindowStart, chosen.start()).toNanos() % 1_800_000_000_000L != 0
        || !chosen.start().isAfter(clock.instant()))
      throw new ApiException(400, "Choose a valid future candidate slot.");
    e.startAt = chosen.start();
    e.endAt = chosen.end();
    e.status = "LOCKED";
    e.updatedAt = clock.instant();
    repository.flush();
    events.changed(e, "EVENT_TIME_CHANGED");
    return e;
  }

  private void open(Event e) {
    events.writable(e);
    if (!e.schedulingMode.equals("FLEXIBLE") || !e.status.equals("OPEN"))
      throw new ApiException(409, "Availability planning is not open.");
  }
}
