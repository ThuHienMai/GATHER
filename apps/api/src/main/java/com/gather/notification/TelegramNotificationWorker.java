package com.gather.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.telegram.TelegramBotClient;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TelegramNotificationWorker {
  private final DeliveryStore store;
  private final NotificationPolicy policy;
  private final TelegramBotClient bot;
  private final ObjectMapper mapper;
  private final JdbcTemplate jdbc;
  private final String username;
  private final String shortName;
  private final boolean configured;

  public TelegramNotificationWorker(
      DeliveryStore store,
      NotificationPolicy policy,
      TelegramBotClient bot,
      ObjectMapper mapper,
      JdbcTemplate jdbc,
      @Value("${gather.telegram.bot-username:}") String username,
      @Value("${gather.telegram.bot-token}") String token,
      @Value("${gather.telegram.mini-app-short-name:gather}") String shortName) {
    this.store = store;
    this.policy = policy;
    this.bot = bot;
    this.mapper = mapper;
    this.jdbc = jdbc;
    this.username = username;
    this.shortName = shortName;
    this.configured = !token.isBlank();
  }

  @Scheduled(initialDelayString = "${gather.worker-initial-delay:0}", fixedDelay = 500)
  public void work() {
    if (!configured) return;
    for (int i = 0; i < 10; i++) {
      var d = store.claim();
      if (d == null) return;
      deliver(d);
    }
  }

  public void deliver(DeliveryStore.Delivery d) {
    try {
      if (!policy.eligible(d.eventId(), d.userId(), d.type())) {
        store.finish(d, "SKIPPED", null, 0);
        return;
      }
      var payload = mapper.readTree(d.payload());
      if (d.type().equals("EVENT_STARTING_SOON")) {
        var current =
            jdbc.queryForMap("SELECT start_at,status FROM events WHERE id=?", d.eventId());
        var start = (java.sql.Timestamp) current.get("start_at");
        if (start == null
            || !Set.of("OPEN", "LOCKED").contains(current.get("status"))
            || !start.toInstant().isAfter(Instant.now())
            || !start.toInstant().toString().equals(payload.path("start_at").asText())) {
          store.finish(d, "SKIPPED", null, 0);
          return;
        }
      }
      String description =
          switch (d.type()) {
            case "EVENT_CANCELLED" -> "was cancelled";
            case "EVENT_TIME_CHANGED" -> "has an updated time";
            case "EVENT_LOCATION_CHANGED" -> "has an updated location";
            case "EVENT_WAITLIST_PROMOTED" -> "has a spot for you — you’re Going";
            case "EVENT_STARTING_SOON" -> "starts soon";
            default -> "has a new planning comment";
          };
      var body = new LinkedHashMap<String, Object>();
      body.put("chat_id", d.telegramId());
      body.put(
          "text",
          payload.path("title").asText("Your event")
              + " "
              + description
              + ". Open Gather for current details.");
      if (!username.isBlank())
        body.put(
            "reply_markup",
            Map.of(
                "inline_keyboard",
                List.of(
                    List.of(
                        Map.of(
                            "text",
                            "Open in Gather",
                            "url",
                            "https://t.me/"
                                + username
                                + "/"
                                + shortName
                                + "?startapp=event_"
                                + d.eventId())))));
      var result = bot.call("sendMessage", body);
      store.finish(d, "SENT", result.path("message_id").asLong(), 0);
    } catch (TelegramBotClient.Failure e) {
      if (e.code == 403) {
        store.disableDm(d.userId());
        store.finish(d, "SKIPPED", null, 0);
      } else if (e.code >= 400 && e.code < 500 && e.code != 429) {
        store.finish(d, "FAILED_FINAL", null, 0);
      } else retry(d, e.retryAfter);
    } catch (Exception e) {
      retry(d, 0);
    }
  }

  private void retry(DeliveryStore.Delivery d, int retryAfter) {
    if (d.attempt() >= 5) {
      store.finish(d, "FAILED_FINAL", null, 0);
      return;
    }
    int[] delays = {5, 30, 120, 600};
    store.finish(d, "RETRY", null, retryAfter > 0 ? retryAfter : delays[d.attempt() - 1]);
  }
}
