package com.gather.common;

import com.gather.realtime.GatherWebSocketHandler;
import io.micrometer.core.instrument.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class GatherMetrics {
  public GatherMetrics(MeterRegistry registry, JdbcTemplate jdbc, GatherWebSocketHandler sockets) {
    Gauge.builder("gather.websocket.connections", sockets, GatherWebSocketHandler::connectionCount)
        .register(registry);
    Gauge.builder(
            "gather.outbox.pending",
            jdbc,
            j ->
                j.queryForObject(
                    "SELECT count(*) FROM outbox_events WHERE processed_at IS NULL", Double.class))
        .register(registry);
    for (String status : new String[] {"SENT", "FAILED_FINAL", "RETRY", "PENDING"})
      Gauge.builder(
              "gather.notifications",
              jdbc,
              j ->
                  j.queryForObject(
                      "SELECT count(*) FROM notification_deliveries WHERE status=?",
                      Double.class,
                      status))
          .tag("status", status)
          .register(registry);
  }
}
