package com.gather.discussion;

import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CommentController {
  private final CommentService comments;

  public CommentController(CommentService comments) {
    this.comments = comments;
  }

  @GetMapping("/events/{id}/comments")
  public List<CommentService.Comment> list(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "GENERAL") String section,
      @RequestParam(defaultValue = "0") int page,
      @AuthenticationPrincipal Jwt jwt) {
    return comments.list(id, UUID.fromString(jwt.getSubject()), section, page);
  }

  @PostMapping("/events/{id}/comments")
  public Map<String, UUID> create(
      @PathVariable UUID id,
      @RequestBody CommentService.CommentInput input,
      @AuthenticationPrincipal Jwt jwt) {
    return Map.of("id", comments.create(id, UUID.fromString(jwt.getSubject()), input));
  }

  public record Edit(String body) {}

  @PatchMapping("/comments/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void edit(
      @PathVariable UUID id, @RequestBody Edit input, @AuthenticationPrincipal Jwt jwt) {
    comments.edit(id, UUID.fromString(jwt.getSubject()), input.body(), false);
  }

  @DeleteMapping("/comments/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    comments.edit(id, UUID.fromString(jwt.getSubject()), null, true);
  }
}
