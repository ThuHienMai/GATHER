package com.gather.community;

import com.gather.common.ApiException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CommunityService {
  private final JdbcTemplate jdbc;

  public CommunityService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Community(UUID id, String name, String timezone, String role) {}

  public List<Community> list(UUID user) {
    return jdbc.query(
        "SELECT c.*,m.role FROM communities c JOIN community_memberships m ON m.community_id=c.id WHERE m.user_id=? AND m.active AND c.bot_active ORDER BY c.name",
        (r, n) ->
            new Community(
                r.getObject("id", UUID.class),
                r.getString("name"),
                r.getString("timezone"),
                r.getString("role")),
        user);
  }

  public void requireMember(UUID community, UUID user) {
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM community_memberships m JOIN communities c ON c.id=m.community_id WHERE m.community_id=? AND m.user_id=? AND m.active AND c.bot_active)",
            Boolean.class,
            community,
            user))) throw new ApiException(403, "Join this Telegram community to access Gather.");
  }

  public boolean admin(UUID community, UUID user) {
    requireMember(community, user);
    return "ADMIN"
        .equals(
            jdbc.queryForObject(
                "SELECT role FROM community_memberships WHERE community_id=? AND user_id=?",
                String.class,
                community,
                user));
  }

  public Community get(UUID id, UUID user) {
    requireMember(id, user);
    return jdbc.queryForObject(
        "SELECT c.*,m.role FROM communities c JOIN community_memberships m ON m.community_id=c.id WHERE c.id=? AND m.user_id=?",
        (r, n) ->
            new Community(id, r.getString("name"), r.getString("timezone"), r.getString("role")),
        id,
        user);
  }
}
