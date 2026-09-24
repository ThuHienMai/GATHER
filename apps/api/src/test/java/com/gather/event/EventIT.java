package com.gather.event;

import static org.assertj.core.api.Assertions.*;

import com.gather.common.ApiException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "gather.jwt-secret=test-only-secret-at-least-thirty-two-bytes-long",
      "gather.telegram.bot-username=gather_test_bot"
    })
class EventIT {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired EventService events;
  @Autowired JdbcTemplate jdbc;
  UUID user = UUID.randomUUID(), community = UUID.randomUUID();

  @BeforeEach
  void setup() {
    jdbc.update(
        "INSERT INTO users(id,telegram_user_id,created_at,updated_at) VALUES (?,?,now(),now())",
        user,
        Math.abs(user.getLeastSignificantBits()));
    jdbc.update(
        "INSERT INTO communities(id,name,slug,telegram_chat_id,timezone,created_at) VALUES (?,'Tokyo',?,?,'Asia/Tokyo',now())",
        community,
        community.toString(),
        Math.abs(community.getLeastSignificantBits()));
    jdbc.update(
        "INSERT INTO community_memberships(community_id,user_id,role,joined_at,telegram_updated_at) VALUES (?,?,'ADMIN',now(),now())",
        community,
        user);
  }

  EventInput input() {
    return new EventInput(
        "Dinner",
        null,
        "Tokyo",
        null,
        "FIXED",
        Instant.now().plusSeconds(3600),
        Instant.now().plusSeconds(7200),
        null,
        null,
        null,
        "Asia/Tokyo",
        8);
  }

  @Test
  void conditionalEditAndAuthorization() {
    var e = events.create(community, user, input());
    assertThat(e.version).isZero();
    var edited = events.edit(e.id, user, "\"0\"", input());
    assertThat(edited.version).isEqualTo(1);
    assertThatThrownBy(() -> events.edit(e.id, user, "\"0\"", input()))
        .isInstanceOf(ApiException.class)
        .satisfies(t -> assertThat(((ApiException) t).getStatusCode().value()).isEqualTo(412));
    assertThatThrownBy(() -> events.edit(e.id, user, null, input()))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> events.get(e.id, UUID.randomUUID())).isInstanceOf(ApiException.class);
    events.transition(e.id, user, "\"1\"", "LOCKED");
    assertThatThrownBy(() -> events.edit(e.id, user, "\"2\"", input()))
        .isInstanceOf(ApiException.class);
  }

  @Autowired RsvpService rsvps;
  @Autowired com.gather.realtime.GatherWebSocketHandler sockets;

  @RepeatedTest(3)
  void lastSeatRaceAndPromotion() throws Exception {
    var in = input();
    var e =
        events.create(
            community,
            user,
            new EventInput(
                in.title(),
                null,
                null,
                null,
                "FIXED",
                in.startAt(),
                in.endAt(),
                null,
                null,
                null,
                "Asia/Tokyo",
                1));
    var people = new ArrayList<UUID>();
    for (int i = 0; i < 20; i++) {
      var id = UUID.randomUUID();
      people.add(id);
      jdbc.update(
          "INSERT INTO users(id,telegram_user_id,created_at,updated_at) VALUES (?,?,now(),now())",
          id,
          Math.abs(id.getLeastSignificantBits()));
      jdbc.update(
          "INSERT INTO community_memberships(community_id,user_id,role,joined_at,telegram_updated_at) VALUES (?,?,'MEMBER',now(),now())",
          community,
          id);
    }
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(20)) {
      var futures = new ArrayList<java.util.concurrent.Future<?>>();
      for (var id : people)
        futures.add(
            pool.submit(
                () -> {
                  try {
                    start.await();
                    rsvps.set(e.id, id, "GOING");
                  } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ex);
                  }
                }));
      start.countDown();
      for (var future : futures) future.get(20, java.util.concurrent.TimeUnit.SECONDS);
    }
    var summary = rsvps.summary(e.id, user);
    assertThat(summary.goingCount()).isEqualTo(1);
    assertThat(summary.waitlistedCount()).isEqualTo(19);
    var first =
        jdbc.queryForObject(
            "SELECT user_id FROM event_rsvps WHERE event_id=? AND status='WAITLISTED' ORDER BY waitlisted_at,user_id LIMIT 1",
            UUID.class,
            e.id);
    var queued =
        jdbc.queryForObject(
            "SELECT waitlisted_at FROM event_rsvps WHERE event_id=? AND user_id=?",
            java.sql.Timestamp.class,
            e.id,
            first);
    rsvps.set(e.id, first, "GOING");
    assertThat(
            jdbc.queryForObject(
                "SELECT waitlisted_at FROM event_rsvps WHERE event_id=? AND user_id=?",
                java.sql.Timestamp.class,
                e.id,
                first))
        .isEqualTo(queued);
    var going =
        summary.participants().stream()
            .filter(p -> p.status().equals("GOING"))
            .findFirst()
            .orElseThrow()
            .userId();
    rsvps.set(e.id, going, "MAYBE");
    assertThat(rsvps.summary(e.id, first).myStatus()).isEqualTo("GOING");
    assertThat(events.get(e.id, user).version).isZero();
    var expanded =
        new EventInput(
            in.title(),
            null,
            null,
            null,
            "FIXED",
            in.startAt(),
            in.endAt(),
            null,
            null,
            null,
            "Asia/Tokyo",
            2);
    events.edit(e.id, user, "\"0\"", expanded);
    assertThat(rsvps.summary(e.id, user).goingCount()).isEqualTo(2);
    var reduced =
        new EventInput(
            in.title(),
            null,
            null,
            null,
            "FIXED",
            in.startAt(),
            in.endAt(),
            null,
            null,
            null,
            "Asia/Tokyo",
            1);
    assertThatThrownBy(() -> events.edit(e.id, user, "\"1\"", reduced))
        .isInstanceOf(ApiException.class)
        .satisfies(t -> assertThat(((ApiException) t).getStatusCode().value()).isEqualTo(409));
    assertThat(events.get(e.id, user).capacity).isEqualTo(2);
  }

  @org.springframework.boot.test.web.server.LocalServerPort int port;
  @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;

  @Test
  void websocketReceivesCommittedRsvpInvalidation() throws Exception {
    var e = events.create(community, user, input());
    var claims =
        org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
            .subject(user.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    var token =
        encoder
            .encode(
                org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                    org.springframework.security.oauth2.jwt.JwsHeader.with(
                            org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                        .build(),
                    claims))
            .getTokenValue();
    var messages = new java.util.concurrent.LinkedBlockingQueue<String>();
    var closed = new java.util.concurrent.CompletableFuture<Integer>();
    var socket =
        java.net.http.HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .header("Origin", "http://localhost:3000")
            .buildAsync(
                java.net.URI.create("ws://localhost:" + port + "/ws"),
                new java.net.http.WebSocket.Listener() {
                  public java.util.concurrent.CompletionStage<?> onClose(
                      java.net.http.WebSocket ws, int status, String reason) {
                    closed.complete(status);
                    return null;
                  }

                  public void onOpen(java.net.http.WebSocket ws) {
                    ws.request(1);
                  }

                  public java.util.concurrent.CompletionStage<?> onText(
                      java.net.http.WebSocket ws, CharSequence data, boolean last) {
                    messages.add(data.toString());
                    ws.request(1);
                    return null;
                  }
                })
            .get(5, java.util.concurrent.TimeUnit.SECONDS);
    socket.sendText("{\"type\":\"AUTH\",\"token\":\"" + token + "\"}", true).join();
    assertThat(messages.poll(5, java.util.concurrent.TimeUnit.SECONDS)).contains("AUTH_OK");
    socket.sendText("{\"type\":\"SUBSCRIBE_EVENT\",\"eventId\":\"" + e.id + "\"}", true).join();
    assertThat(messages.poll(5, java.util.concurrent.TimeUnit.SECONDS)).contains("SUBSCRIBED");
    rsvps.set(e.id, user, "GOING");
    dispatcher.dispatch();
    String update;
    do {
      update = messages.poll(5, java.util.concurrent.TimeUnit.SECONDS);
    } while (update != null && !update.contains("RSVP_UPDATED"));
    assertThat(update).contains("RSVP_UPDATED");
    // A subscribed client must lose access even if a later broadcast races the sweep.
    jdbc.update(
        "UPDATE community_memberships SET active=FALSE WHERE community_id=? AND user_id=?",
        community,
        user);
    sockets.broadcast(e.id, "RSVP_UPDATED", UUID.randomUUID(), Instant.now());
    assertThat(closed.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(1008);
    assertThatThrownBy(() -> rsvps.summary(e.id, user)).isInstanceOf(ApiException.class);
  }

  @Autowired com.gather.discussion.CommentService comments;

  @Test
  void structuredRepliesAndSoftDeletion() {
    var e = events.create(community, user, input());
    var parent =
        comments.create(
            e.id,
            user,
            new com.gather.discussion.CommentService.CommentInput(
                "TIME", null, "Could we start later?"));
    var reply =
        comments.create(
            e.id,
            user,
            new com.gather.discussion.CommentService.CommentInput("TIME", parent, "Yes"));
    assertThatThrownBy(
            () ->
                comments.create(
                    e.id,
                    user,
                    new com.gather.discussion.CommentService.CommentInput(
                        "GENERAL", parent, "Wrong section")))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                comments.create(
                    e.id,
                    user,
                    new com.gather.discussion.CommentService.CommentInput(
                        "TIME", reply, "Too deep")))
        .isInstanceOf(ApiException.class);
    comments.edit(parent, user, null, true);
    assertThat(comments.list(e.id, user, "TIME", 0).getFirst().body()).isEqualTo("[deleted]");
    assertThat(comments.list(e.id, user, "TIME", 0)).hasSize(2);
  }

  @Autowired com.gather.scheduling.SchedulingService scheduling;

  @Test
  void thirtyMinuteDurationIsValidAndWindowErrorsAreSpecific() {
    var start = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    java.util.function.BiFunction<Instant, Instant, EventInput> flexible =
        (from, to) ->
            new EventInput(
                "Short meetup",
                null,
                null,
                null,
                "FLEXIBLE",
                null,
                null,
                from,
                to,
                30,
                "Asia/Tokyo",
                null);
    assertThat(
            events.create(community, user, flexible.apply(start, start.plusSeconds(1800)))
                .durationMinutes)
        .isEqualTo(30);
    assertThatThrownBy(
            () -> events.create(community, user, flexible.apply(start, start.plusSeconds(1799))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("at least 30 minutes");
    assertThatThrownBy(() -> events.create(community, user, flexible.apply(start, start)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Ends must be later than Starts");
    assertThatThrownBy(
            () ->
                events.create(
                    community, user, flexible.apply(start, start.plusSeconds(14 * 86400 + 1))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot exceed 14 days");
    assertThatThrownBy(
            () ->
                events.create(
                    community,
                    user,
                    flexible.apply(start.minusSeconds(7200), start.minusSeconds(5400))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Ends must be in the future");
  }

  @Test
  void availabilityIsCanonicalPrivateAndFinalizationLocks() {
    // PostgreSQL stores microseconds; Linux clocks can supply nanoseconds that round
    // across the window boundary when the event is persisted and read back.
    var start = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS).plusSeconds(3600);
    var end = start.plusSeconds(14400);
    var e =
        events.create(
            community,
            user,
            new EventInput(
                "Karaoke",
                null,
                null,
                null,
                "FLEXIBLE",
                null,
                null,
                start,
                end,
                120,
                "Asia/Tokyo",
                null));
    rsvps.set(e.id, user, "GOING");
    var intervals =
        List.of(
            new com.gather.scheduling.RangeAddSchedulingEngine.Interval(
                start, start.plusSeconds(3600)),
            new com.gather.scheduling.RangeAddSchedulingEngine.Interval(
                start.plusSeconds(3600), end));
    assertThat(scheduling.replace(e.id, user, intervals)).hasSize(1);
    assertThat(scheduling.recommendations(e.id, user).getFirst().goingAvailable()).isEqualTo(1);
    scheduling.finalize(
        e.id,
        user,
        "\"0\"",
        new com.gather.scheduling.RangeAddSchedulingEngine.Interval(
            start, start.plusSeconds(7200)));
    assertThat(events.get(e.id, user).status).isEqualTo("LOCKED");
    assertThatThrownBy(() -> scheduling.replace(e.id, user, intervals))
        .isInstanceOf(ApiException.class);
  }

  @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
  @Autowired com.gather.notification.OutboxDispatcher dispatcher;

  @Test
  void outboxIsAtomicWithMutation() {
    var before = jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class);
    assertThatThrownBy(
            () ->
                transactions.execute(
                    status -> {
                      events.create(community, user, input());
                      throw new IllegalStateException("simulate failure");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class))
        .isEqualTo(before);
    var e = events.create(community, user, input());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE aggregate_id=?", Integer.class, e.id))
        .isEqualTo(1);
  }

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  com.gather.telegram.TelegramBotClient bot;

  @Autowired com.gather.notification.DeliveryStore deliveries;
  @Autowired com.gather.notification.TelegramNotificationWorker worker;
  @Autowired com.gather.event.EventLifecycleWorker lifecycle;

  @Test
  void durableNotificationDedupRetryAndLeaseRecovery() throws Exception {
    jdbc.update("UPDATE users SET telegram_dm_enabled=TRUE WHERE id=?", user);
    var e = events.create(community, user, input());
    events.transition(e.id, user, "\"0\"", "CANCELLED");
    dispatcher.dispatch();
    var outbox =
        jdbc.queryForObject(
            "SELECT id FROM outbox_events WHERE aggregate_id=? AND event_type='EVENT_CANCELLED'",
            UUID.class,
            e.id);
    jdbc.update("UPDATE outbox_events SET processed_at=NULL WHERE id=?", outbox);
    dispatcher.dispatch();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notification_deliveries WHERE outbox_event_id=?",
                Integer.class,
                outbox))
        .isEqualTo(1);
    var first = deliveries.claim();
    assertThat(first).isNotNull();
    org.mockito.Mockito.when(
            bot.call(
                org.mockito.ArgumentMatchers.eq("sendMessage"),
                org.mockito.ArgumentMatchers.anyMap()))
        .thenThrow(new com.gather.telegram.TelegramBotClient.Failure(429, 42));
    worker.deliver(first);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM notification_deliveries WHERE id=?", String.class, first.id()))
        .isEqualTo("RETRY");
    assertThat(
            jdbc.queryForObject(
                "SELECT extract(epoch from next_attempt_at-now())::int FROM notification_deliveries WHERE id=?",
                Integer.class,
                first.id()))
        .isBetween(35, 43);
    jdbc.update(
        "UPDATE notification_deliveries SET next_attempt_at=now()-interval '1 second' WHERE id=?",
        first.id());
    var second = deliveries.claim();
    jdbc.update(
        "UPDATE notification_deliveries SET locked_until=now()-interval '1 second' WHERE id=?",
        second.id());
    var recovered = deliveries.claim();
    assertThat(recovered.lease()).isNotEqualTo(second.lease());
    deliveries.finish(second, "SENT", 1L, 0);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM notification_deliveries WHERE id=?", String.class, second.id()))
        .isEqualTo("IN_PROGRESS");
    jdbc.update("INSERT INTO notification_preferences VALUES (?,?,'MUTED')", e.id, user);
    worker.deliver(recovered);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM notification_deliveries WHERE id=?", String.class, second.id()))
        .isEqualTo("SKIPPED");
  }

  @Autowired com.gather.notification.NotificationController notificationApi;

  @Test
  void blockedBotDisablesDmAndInactiveCommunityHidesHistory() throws Exception {
    jdbc.update("UPDATE users SET telegram_dm_enabled=TRUE WHERE id=?", user);
    var e = events.create(community, user, input());
    events.transition(e.id, user, "\"0\"", "CANCELLED");
    dispatcher.dispatch();
    var delivery = deliveries.claim();
    assertThat(delivery.eventId()).isEqualTo(e.id);
    org.mockito.Mockito.when(
            bot.call(
                org.mockito.ArgumentMatchers.eq("sendMessage"),
                org.mockito.ArgumentMatchers.anyMap()))
        .thenThrow(new com.gather.telegram.TelegramBotClient.Failure(403, 0));
    worker.deliver(delivery);
    assertThat(
            jdbc.queryForObject(
                "SELECT telegram_dm_enabled FROM users WHERE id=?", Boolean.class, user))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM notification_deliveries WHERE id=?",
                String.class,
                delivery.id()))
        .isEqualTo("SKIPPED");
    var jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test-only")
            .header("alg", "HS256")
            .subject(user.toString())
            .build();
    assertThat(notificationApi.list(jwt, 0)).hasSize(1);
    jdbc.update("UPDATE communities SET bot_active=FALSE WHERE id=?", community);
    assertThat(notificationApi.list(jwt, 0)).isEmpty();
  }

  @Test
  void remindersAreUniqueAndPastEventsComplete() {
    var e =
        events.create(
            community,
            user,
            new EventInput(
                "Soon",
                null,
                null,
                null,
                "FIXED",
                Instant.now().plusSeconds(600),
                Instant.now().plusSeconds(3600),
                null,
                null,
                null,
                "Asia/Tokyo",
                null));
    lifecycle.tick();
    lifecycle.tick();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='EVENT_STARTING_SOON'",
                Integer.class,
                e.id))
        .isEqualTo(1);
    jdbc.update(
        "UPDATE events SET start_at=now()-interval '2 hours',end_at=now()-interval '1 hour' WHERE id=?",
        e.id);
    lifecycle.tick();
    assertThat(events.get(e.id, user).status).isEqualTo("COMPLETED");
  }

  @Autowired com.gather.telegram.InlineSharingService sharing;

  @Test
  void inlineSharingRequiresMembership() {
    var e = events.create(community, user, input());
    long telegram =
        jdbc.queryForObject("SELECT telegram_user_id FROM users WHERE id=?", Long.class, user);
    assertThat(sharing.results(telegram, "event_" + e.id)).hasSize(1);
    assertThat(sharing.results(99999, "event_" + e.id)).isEmpty();
    jdbc.update("UPDATE community_memberships SET active=FALSE WHERE user_id=?", user);
    assertThat(sharing.results(telegram, "event_" + e.id)).isEmpty();
  }
}
