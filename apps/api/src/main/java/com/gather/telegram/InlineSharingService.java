package com.gather.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.gather.auth.UserRepository;
import com.gather.event.EventService;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InlineSharingService {
  private final UserRepository users;
  private final EventService events;
  private final TelegramBotClient bot;
  private final String username;
  private final String shortName;

  public InlineSharingService(
      UserRepository users,
      EventService events,
      TelegramBotClient bot,
      @Value("${gather.telegram.bot-username:}") String username,
      @Value("${gather.telegram.mini-app-short-name:gather}") String shortName) {
    this.users = users;
    this.events = events;
    this.bot = bot;
    this.username = username;
    this.shortName = shortName;
  }

  public List<Map<String, Object>> results(long telegramUser, String query) {
    try {
      if (!query.startsWith("event_") || username.isBlank()) return List.of();
      var user = users.findByTelegramUserId(telegramUser).orElseThrow();
      var e = events.get(UUID.fromString(query.substring(6)), user.id);
      var when =
          e.startAt == null
              ? "Time not finalized"
              : DateTimeFormatter.ofPattern("EEE MMM d · HH:mm z", Locale.ENGLISH)
                  .withZone(ZoneId.of(e.timezone))
                  .format(e.startAt);
      var button =
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
                              + e.id))));
      return List.of(
          Map.of(
              "type",
              "article",
              "id",
              e.id.toString(),
              "title",
              e.title,
              "description",
              when,
              "input_message_content",
              Map.of(
                  "message_text",
                  e.title
                      + "\n"
                      + when
                      + "\n"
                      + Objects.toString(e.locationText, "Location to be decided")),
              "reply_markup",
              button));
    } catch (com.gather.common.ApiException | IllegalArgumentException | NoSuchElementException e) {
      return List.of();
    }
  }

  public void answer(JsonNode query) {
    bot.call(
        "answerInlineQuery",
        Map.of(
            "inline_query_id",
            query.path("id").asText(),
            "results",
            results(query.path("from").path("id").asLong(), query.path("query").asText()),
            "cache_time",
            0,
            "is_personal",
            true));
  }
}
