package com.gather.community;

import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/communities")
public class CommunityController {
  private final CommunityService service;

  public CommunityController(CommunityService service) {
    this.service = service;
  }

  @GetMapping
  public List<CommunityService.Community> list(@AuthenticationPrincipal Jwt jwt) {
    return service.list(UUID.fromString(jwt.getSubject()));
  }

  @GetMapping("/{id}")
  public CommunityService.Community get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return service.get(id, UUID.fromString(jwt.getSubject()));
  }
}
