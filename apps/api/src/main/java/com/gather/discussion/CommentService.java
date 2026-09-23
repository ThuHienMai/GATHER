package com.gather.discussion;

import com.gather.common.ApiException;
import com.gather.event.EventService;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {
  private final JdbcTemplate jdbc;
  private final EventService events;

  public CommentService(JdbcTemplate jdbc, EventService events) {
    this.jdbc = jdbc;
    this.events = events;
  }

  public record Comment(
      UUID id,
      UUID eventId,
      UUID authorId,
      String authorName,
      String section,
      UUID parentCommentId,
      String body,
      Instant createdAt,
      Instant editedAt,
      Instant deletedAt) {}

  public record CommentInput(String section, UUID parentCommentId, String body) {}

  private Comment map(java.sql.ResultSet r, int n) throws java.sql.SQLException {
    return new Comment(
        r.getObject("id", UUID.class),
        r.getObject("event_id", UUID.class),
        r.getObject("author_id", UUID.class),
        r.getString("first_name"),
        r.getString("section"),
        r.getObject("parent_comment_id", UUID.class),
        r.getTimestamp("deleted_at") == null ? r.getString("body") : "[deleted]",
        r.getTimestamp("created_at").toInstant(),
        r.getTimestamp("edited_at") == null ? null : r.getTimestamp("edited_at").toInstant(),
        r.getTimestamp("deleted_at") == null ? null : r.getTimestamp("deleted_at").toInstant());
  }

  public List<Comment> list(UUID event, UUID user, String section, int page) {
    events.get(event, user);
    if (page < 0 || page > 100000) throw new ApiException(400, "Invalid page.");
    return jdbc.query(
        "SELECT c.*,u.first_name FROM comments c JOIN users u ON u.id=c.author_id WHERE c.event_id=? AND c.section=? ORDER BY c.created_at,c.id LIMIT 50 OFFSET ?",
        this::map,
        event,
        section,
        page * 50);
  }

  private String body(String value) {
    if (value == null || value.isBlank() || value.length() > 2000)
      throw new ApiException(400, "Comments must contain 1–2000 characters.");
    return value.strip();
  }

  @Transactional
  public UUID create(UUID event, UUID user, CommentInput input) {
    var e = events.locked(event, user);
    events.writable(e);
    if (input.section() == null || !Set.of("GENERAL", "TIME", "LOCATION").contains(input.section()))
      throw new ApiException(400, "Invalid discussion section.");
    if (input.parentCommentId() != null) {
      var parents =
          jdbc.queryForList(
              "SELECT id FROM comments WHERE id=? AND event_id=? AND section=? AND parent_comment_id IS NULL AND deleted_at IS NULL",
              UUID.class,
              input.parentCommentId(),
              event,
              input.section());
      if (parents.isEmpty())
        throw new ApiException(400, "Reply to a top-level comment in this section.");
    }
    var id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO comments(id,event_id,author_id,section,parent_comment_id,body,created_at) VALUES (?,?,?,?,?,?,now())",
        id,
        event,
        user,
        input.section(),
        input.parentCommentId(),
        body(input.body()));
    events.changed(e, "COMMENT_CREATED");
    return id;
  }

  @Transactional
  public void edit(UUID id, UUID user, String text, boolean delete) {
    var rows = jdbc.queryForList("SELECT event_id FROM comments WHERE id=?", UUID.class, id);
    if (rows.isEmpty()) throw new ApiException(404, "Comment not found.");
    var e = events.locked(rows.getFirst(), user);
    events.writable(e);
    int updated =
        delete
            ? jdbc.update(
                "UPDATE comments SET deleted_at=now() WHERE id=? AND author_id=? AND deleted_at IS NULL",
                id,
                user)
            : jdbc.update(
                "UPDATE comments SET body=?,edited_at=now() WHERE id=? AND author_id=? AND deleted_at IS NULL",
                body(text),
                id,
                user);
    if (updated == 0) throw new ApiException(403, "Only the author can change an active comment.");
    events.changed(e, "COMMENT_UPDATED");
  }
}
