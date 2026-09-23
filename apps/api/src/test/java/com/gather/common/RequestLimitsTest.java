package com.gather.common;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RequestLimitsTest {
  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authenticatedUsersDoNotShareProxyLimit() throws Exception {
    var filter = new RequestLimits();
    for (var user : List.of("alice", "bob")) {
      SecurityContextHolder.getContext()
          .setAuthentication(
              UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
      for (int i = 0; i < 120; i++) {
        var request = new MockHttpServletRequest("POST", "/api/v1/events");
        request.setContent(new byte[0]);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (a, b) -> {});
        assertThat(response.getStatus()).isEqualTo(200);
      }
      var request = new MockHttpServletRequest("POST", "/api/v1/events");
      request.setContent(new byte[0]);
      var response = new MockHttpServletResponse();
      filter.doFilter(request, response, (a, b) -> {});
      assertThat(response.getStatus()).isEqualTo(429);
    }
  }
}
