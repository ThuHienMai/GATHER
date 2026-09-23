package com.gather.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.event.EventService;
import com.gather.telegram.TelegramUpdateService;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class GatherWebSocketHandler extends TextWebSocketHandler {
  private static class Client {
    final WebSocketSession session;
    final Instant connected = Instant.now();
    UUID user;
    Instant expires;
    Instant lastMessage = Instant.now();
    final Set<UUID> events = ConcurrentHashMap.newKeySet();

    Client(WebSocketSession s) {
      session = s;
    }
  }

  private final Map<String, Client> clients = new ConcurrentHashMap<>();
  private final ObjectMapper mapper;
  private final JwtDecoder decoder;
  private final EventService events;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public GatherWebSocketHandler(
      ObjectMapper mapper,
      JwtDecoder decoder,
      EventService events,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.mapper = mapper;
    this.decoder = decoder;
    this.events = events;
    this.jdbc = jdbc;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    session.setTextMessageSizeLimit(8192);
    clients.put(session.getId(), new Client(session));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    var c = clients.get(session.getId());
    if (c == null) return;
    try {
      var json = mapper.readTree(message.getPayload());
      String type = json.path("type").asText();
      c.lastMessage = Instant.now();
      if (c.user == null) {
        if (!type.equals("AUTH")) throw new IllegalArgumentException();
        var jwt = decoder.decode(json.path("token").asText());
        c.user = UUID.fromString(jwt.getSubject());
        c.expires = jwt.getExpiresAt();
        if (c.expires == null) throw new IllegalArgumentException();
        send(c, Map.of("type", "AUTH_OK"));
        return;
      }
      if (!c.expires.isAfter(Instant.now())) throw new IllegalArgumentException();
      switch (type) {
        case "SUBSCRIBE_EVENT" -> {
          if (c.events.size() >= 25) throw new IllegalArgumentException();
          var id = UUID.fromString(json.path("eventId").asText());
          events.get(id, c.user);
          c.events.add(id);
          send(c, Map.of("type", "SUBSCRIBED", "eventId", id));
        }
        case "UNSUBSCRIBE_EVENT" -> c.events.remove(UUID.fromString(json.path("eventId").asText()));
        case "PING" -> send(c, Map.of("type", "PONG"));
        case "PONG" -> {}
        default -> throw new IllegalArgumentException();
      }
    } catch (Exception e) {
      close(c);
    }
  }

  private void send(Client c, Object value) {
    try {
      synchronized (c.session) {
        if (c.session.isOpen())
          c.session.sendMessage(new TextMessage(mapper.writeValueAsString(value)));
      }
    } catch (Exception e) {
      close(c);
    }
  }

  private void close(Client c) {
    clients.remove(c.session.getId());
    try {
      c.session.close(CloseStatus.POLICY_VIOLATION);
    } catch (Exception ignored) {
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    clients.remove(session.getId());
  }

  @TransactionalEventListener
  public void membership(TelegramUpdateService.MembershipChanged change) {
    for (var c : clients.values()) if (change.userId().equals(c.user)) recheck(c);
  }

  public void broadcast(UUID id, String type, UUID changeId, Instant occurredAt) {
    if (clients.values().stream().noneMatch(c -> c.events.contains(id))) return;
    // Recheck membership once per event, rather than once per connected client.
    var members =
        new HashSet<>(
            jdbc.queryForList(
                "SELECT m.user_id FROM community_memberships m JOIN communities c ON c.id=m.community_id JOIN events e ON e.community_id=c.id WHERE e.id=? AND m.active AND c.bot_active",
                UUID.class,
                id));
    for (var c : clients.values())
      if (c.events.contains(id)) {
        try {
          if (!members.contains(c.user) || !c.expires.isAfter(Instant.now())) {
            close(c);
            continue;
          }
          send(
              c,
              Map.of("type", type, "eventId", id, "changeId", changeId, "occurredAt", occurredAt));
        } catch (Exception e) {
          close(c);
        }
      }
  }

  private void recheck(Client c) {
    for (var id : c.events)
      try {
        events.get(id, c.user);
      } catch (Exception e) {
        close(c);
        return;
      }
  }

  @Scheduled(initialDelayString = "${gather.worker-initial-delay:0}", fixedDelay = 5000)
  public void sweep() {
    var now = Instant.now();
    for (var c : clients.values()) {
      if ((c.user == null && c.connected.plusSeconds(10).isBefore(now))
          || (c.expires != null && !c.expires.isAfter(now))
          || c.lastMessage.plusSeconds(90).isBefore(now)) {
        close(c);
        continue;
      }
      if (c.user != null) recheck(c);
    }
  }

  @Scheduled(initialDelayString = "${gather.worker-initial-delay:0}", fixedDelay = 30000)
  public void heartbeat() {
    for (var c : clients.values()) if (c.user != null) send(c, Map.of("type", "PING"));
  }

  public int connectionCount() {
    return clients.size();
  }
}
