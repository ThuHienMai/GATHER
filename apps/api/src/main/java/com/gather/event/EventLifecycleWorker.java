package com.gather.event;

import com.gather.notification.OutboxRecorder;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EventLifecycleWorker {
  private final JdbcTemplate jdbc;
  private final EventRepository events;
  private final EventService service;
  private final OutboxRecorder outbox;
  private final Clock clock;

  public EventLifecycleWorker(
      JdbcTemplate jdbc,
      EventRepository events,
      EventService service,
      OutboxRecorder outbox,
      Clock clock) {
    this.jdbc = jdbc;
    this.events = events;
    this.service = service;
    this.outbox = outbox;
    this.clock = clock;
  }

  @Scheduled(initialDelayString = "${gather.worker-initial-delay:0}", fixedDelay = 60000)
  @Transactional
  public void tick() {
    var ids =
        jdbc.queryForList(
            "SELECT id FROM events WHERE status IN ('OPEN','LOCKED') AND (coalesce(end_at,flex_window_end)<=now() OR (start_at>now() AND start_at<=now()+interval '30 minutes')) ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED",
            UUID.class);
    for (var id : ids) {
      var e = events.findById(id).orElseThrow();
      if (!service.effectiveEnd(e).isAfter(clock.instant())) {
        e.status = "COMPLETED";
        e.updatedAt = clock.instant();
        events.flush();
        service.changed(e, "EVENT_COMPLETED");
      } else if (jdbc.queryForObject(
              "SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='EVENT_STARTING_SOON' AND payload->>'start_at'=?",
              Integer.class,
              id,
              e.startAt.toString())
          == 0) outbox.record(id, "EVENT_STARTING_SOON", null);
    }
  }
}
