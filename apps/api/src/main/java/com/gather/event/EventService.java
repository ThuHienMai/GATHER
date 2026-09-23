package com.gather.event;

import com.gather.common.ApiException;
import com.gather.community.CommunityService;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
  public record Changed(UUID eventId, String type, Map<String, Object> snapshot) {}

  private final EventRepository events;
  private final CommunityService communities;
  private final Clock clock;
  private final ApplicationEventPublisher publisher;
  private final JdbcTemplate jdbc;
  private final AttendanceOperations attendance;

  public EventService(
      EventRepository events,
      CommunityService communities,
      Clock clock,
      ApplicationEventPublisher publisher,
      JdbcTemplate jdbc,
      AttendanceOperations attendance) {
    this.events = events;
    this.communities = communities;
    this.clock = clock;
    this.publisher = publisher;
    this.jdbc = jdbc;
    this.attendance = attendance;
  }

  public Event get(UUID id, UUID user) {
    var e = events.findById(id).orElseThrow(() -> new ApiException(404, "Event not found."));
    communities.requireMember(e.communityId, user);
    return e;
  }

  public Event locked(UUID id, UUID user) {
    var e = events.lock(id).orElseThrow(() -> new ApiException(404, "Event not found."));
    communities.requireMember(e.communityId, user);
    return e;
  }

  public void organizer(Event e, UUID user) {
    if (!e.organizerId.equals(user) && !communities.admin(e.communityId, user))
      throw new ApiException(403, "Only the organizer or a community administrator can do this.");
  }

  public void writable(Event e) {
    if (Set.of("CANCELLED", "COMPLETED").contains(e.status)
        || !effectiveEnd(e).isAfter(clock.instant()))
      throw new ApiException(409, "This event is no longer accepting changes.");
  }

  public Instant effectiveEnd(Event e) {
    return e.endAt != null ? e.endAt : e.flexWindowEnd;
  }

  public void match(Event e, String header) {
    if (header == null) throw new ApiException(428, "If-Match is required.");
    if (!header.equals("\"" + e.version + "\""))
      throw new ApiException(
          412, "This event changed. Review the latest version before saving again.");
  }

  public void changed(Event e, String type) {
    var snapshot = new LinkedHashMap<String, Object>();
    snapshot.put("title", e.title);
    snapshot.put("community_id", e.communityId.toString());
    snapshot.put("start_at", e.startAt == null ? null : e.startAt.toString());
    snapshot.put("end_at", e.endAt == null ? null : e.endAt.toString());
    snapshot.put("location_text", e.locationText);
    snapshot.put("status", e.status);
    snapshot.put("version", Long.toString(e.version));
    publisher.publishEvent(new Changed(e.id, type, Collections.unmodifiableMap(snapshot)));
  }

  @Transactional
  public Event create(UUID community, UUID user, EventInput input) {
    communities.requireMember(community, user);
    var e = new Event();
    e.id = UUID.randomUUID();
    e.communityId = community;
    e.organizerId = user;
    e.status = "OPEN";
    e.createdAt = clock.instant();
    apply(e, input);
    events.saveAndFlush(e);
    changed(e, "EVENT_CREATED");
    return e;
  }

  @Transactional
  public Event edit(UUID id, UUID user, String version, EventInput input) {
    var e = locked(id, user);
    organizer(e, user);
    match(e, version);
    writable(e);
    if (!e.status.equals("OPEN")) throw new ApiException(409, "Planning is locked.");
    var oldStart = e.startAt;
    var oldEnd = e.endAt;
    var oldWindowStart = e.flexWindowStart;
    var oldWindowEnd = e.flexWindowEnd;
    var oldLocation = e.locationText;
    var oldLocationUrl = e.locationUrl;
    attendance.checkCapacity(e, input.capacity());
    apply(e, input);
    events.flush();
    attendance.promote(e);
    changed(
        e,
        !Objects.equals(oldStart, e.startAt)
                || !Objects.equals(oldEnd, e.endAt)
                || !Objects.equals(oldWindowStart, e.flexWindowStart)
                || !Objects.equals(oldWindowEnd, e.flexWindowEnd)
            ? "EVENT_TIME_CHANGED"
            : !Objects.equals(oldLocation, e.locationText)
                    || !Objects.equals(oldLocationUrl, e.locationUrl)
                ? "EVENT_LOCATION_CHANGED"
                : "EVENT_UPDATED");
    return e;
  }

  @Transactional
  public Event transition(UUID id, UUID user, String version, String status) {
    var e = locked(id, user);
    organizer(e, user);
    match(e, version);
    writable(e);
    if (status.equals("LOCKED") && e.startAt == null)
      throw new ApiException(409, "Finalize a time before locking.");
    e.status = status;
    e.updatedAt = clock.instant();
    events.flush();
    changed(e, "EVENT_" + status);
    return e;
  }

  public List<Event> list(UUID community, UUID user, String view, int page) {
    communities.requireMember(community, user);
    if (page < 0 || page > 100000) throw new ApiException(400, "Invalid page.");
    String condition =
        view.equals("past")
            ? "(status IN ('CANCELLED','COMPLETED') OR coalesce(end_at,flex_window_end)<=now())"
            : "status IN ('OPEN','LOCKED') AND coalesce(end_at,flex_window_end)>now()";
    var ids =
        jdbc.queryForList(
            "SELECT id FROM events WHERE community_id=? AND "
                + condition
                + " ORDER BY coalesce(start_at,flex_window_start),id LIMIT 30 OFFSET ?",
            UUID.class,
            community,
            page * 30);
    var found = events.findAllById(ids);
    found.sort(Comparator.comparingInt(e -> ids.indexOf(e.id)));
    return found;
  }

  private void apply(Event e, EventInput in) {
    ZoneId.of(in.timezone());
    if (in.locationUrl() != null && !in.locationUrl().isBlank()) {
      var uri = java.net.URI.create(in.locationUrl());
      if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null)
        throw new ApiException(400, "Location URL must use HTTP or HTTPS.");
    }
    if (e.schedulingMode != null && !e.schedulingMode.equals(in.schedulingMode()))
      jdbc.update("DELETE FROM availability_intervals WHERE event_id=?", e.id);
    if ("FIXED".equals(in.schedulingMode())) {
      if (in.startAt() == null
          || in.endAt() == null
          || !in.endAt().isAfter(in.startAt())
          || !in.endAt().isAfter(clock.instant()))
        throw new ApiException(400, "Choose a valid future time range.");
      e.flexWindowStart = null;
      e.flexWindowEnd = null;
      e.durationMinutes = null;
    } else if ("FLEXIBLE".equals(in.schedulingMode())) {
      if (in.flexWindowStart() == null
          || in.flexWindowEnd() == null
          || !in.flexWindowEnd().isAfter(in.flexWindowStart())
          || !in.flexWindowEnd().isAfter(clock.instant())
          || Duration.between(in.flexWindowStart(), in.flexWindowEnd())
                  .compareTo(Duration.ofDays(14))
              > 0
          || in.durationMinutes() == null
          || in.durationMinutes() < 30
          || in.durationMinutes() > 480
          || Duration.between(in.flexWindowStart(), in.flexWindowEnd())
                  .compareTo(Duration.ofMinutes(in.durationMinutes()))
              < 0) throw new ApiException(400, "Invalid flexible scheduling window or duration.");
      if (e.flexWindowStart != null
          && (!e.flexWindowStart.equals(in.flexWindowStart())
              || !e.flexWindowEnd.equals(in.flexWindowEnd())))
        jdbc.update("DELETE FROM availability_intervals WHERE event_id=?", e.id);
      e.flexWindowStart = in.flexWindowStart();
      e.flexWindowEnd = in.flexWindowEnd();
      e.durationMinutes = in.durationMinutes();
    } else throw new ApiException(400, "Invalid scheduling mode.");
    e.title = in.title().strip();
    if (e.title.length() < 3)
      throw new ApiException(400, "Title must contain at least three characters.");
    e.description = in.description();
    e.locationText = in.locationText();
    e.locationUrl = in.locationUrl();
    e.schedulingMode = in.schedulingMode();
    e.startAt = "FIXED".equals(in.schedulingMode()) ? in.startAt() : null;
    e.endAt = "FIXED".equals(in.schedulingMode()) ? in.endAt() : null;
    e.timezone = in.timezone();
    e.capacity = in.capacity();
    e.updatedAt = clock.instant();
  }
}
