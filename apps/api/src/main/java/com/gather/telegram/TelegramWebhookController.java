package com.gather.telegram;

import com.fasterxml.jackson.databind.*;
import com.gather.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
public class TelegramWebhookController {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(TelegramWebhookController.class);
  private final String secret;
  private final ObjectMapper mapper;
  private final TelegramBotClient bot;
  private final TelegramUpdateService updates;
  private final InlineSharingService sharing;

  public TelegramWebhookController(
      @Value("${gather.telegram.webhook-secret}") String secret,
      ObjectMapper mapper,
      TelegramBotClient bot,
      TelegramUpdateService updates,
      InlineSharingService sharing) {
    this.secret = secret;
    this.mapper = mapper;
    this.bot = bot;
    this.updates = updates;
    this.sharing = sharing;
  }

  @PostMapping("/api/v1/telegram/webhook")
  public Map<String, Object> receive(
      @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", defaultValue = "") String supplied,
      @RequestBody byte[] body)
      throws Exception {
    if (secret.isBlank()
        || !MessageDigest.isEqual(
            secret.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
      throw new ApiException(401, "Invalid webhook secret.");
    if (body.length > 131072) throw new ApiException(413, "Webhook too large.");
    var update = mapper.readTree(body);
    var message = update.path("message");
    var command = message.path("text").asText("").split("[ @]", 2)[0];
    try {
      return process(update, message, command);
    } catch (ApiException e) {
      log.warn(
          "telegram_update_rejected updateId={} command={} status={} reason={}",
          update.path("update_id").asLong(),
          Set.of("/setup", "/join", "/start", "/help").contains(command) ? command : "other",
          e.getStatusCode().value(),
          e.getReason());
      // Terminal command errors must not keep Telegram retrying an unchanged message.
      // The service transaction has rolled back before this separate acknowledgement.
      if (Set.of("/setup", "/join").contains(command)
          && Set.of(400, 403).contains(e.getStatusCode().value())
          && update.path("update_id").isIntegralNumber()
          && message.path("chat").path("id").isIntegralNumber()) {
        if (updates.acknowledgeRejected(update.path("update_id").asLong())) {
          // Telegram supports a Bot API method in a successful webhook response.
          return Map.of(
              "method",
              "sendMessage",
              "chat_id",
              message.path("chat").path("id").asLong(),
              "text",
              e.getReason());
        }
        return Map.of("ok", true);
      }
      throw e;
    } catch (TelegramBotClient.Failure e) {
      log.warn(
          "telegram_api_failed updateId={} code={} retryAfter={}",
          update.path("update_id").asLong(),
          e.code,
          e.retryAfter);
      throw e;
    } catch (IllegalArgumentException e) {
      log.warn(
          "telegram_update_invalid updateId={} exceptionType={}",
          update.path("update_id").asLong(),
          e.getClass().getSimpleName());
      throw e;
    }
  }

  private Map<String, Object> process(JsonNode update, JsonNode message, String command) {
    String role = "MEMBER";
    if (Set.of("/setup", "/join").contains(command)) {
      if (message.has("sender_chat"))
        throw new ApiException(400, "Use your personal Telegram identity for this command.");
      long chat = message.path("chat").path("id").asLong();
      long user = message.path("from").path("id").asLong();
      var status = bot.status(chat, user);
      if (!Set.of("creator", "administrator", "member").contains(status))
        throw new ApiException(403, "Group membership required.");
      role = TelegramUpdateService.isAdmin(status) ? "ADMIN" : "MEMBER";
      if (!TelegramUpdateService.isAdmin(bot.status(chat, bot.botId())))
        throw new ApiException(403, "Give the Gather bot administrator access first.");
    }
    boolean processed = updates.process(update, role);
    if (update.has("inline_query")) {
      sharing.answer(update.path("inline_query"));
      return Map.of("ok", true);
    }
    if (processed && Set.of("/setup", "/join", "/start", "/help").contains(command)) {
      bot.call(
          "sendMessage",
          Map.of(
              "chat_id",
              message.path("chat").path("id").asLong(),
              "text",
              switch (command) {
                case "/setup" -> "Gather is ready. Members can run /join to enroll.";
                case "/join" -> "You joined Gather. Open the Mini App to see your community.";
                case "/start" ->
                    "Welcome to Gather. Notifications are enabled. Run /join in your group to get started.";
                default ->
                    "Gather: admins run /setup in a group; members run /join; open the Mini App to plan events. Start this bot privately to enable notifications.";
              }));
    }
    return Map.of("ok", true);
  }
}
