package com.gather.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestLimits extends OncePerRequestFilter {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(RequestLimits.class);

  private record Bucket(long minute, int count) {}

  private final Map<String, Bucket> buckets = new HashMap<>();

  private synchronized boolean allowed(String key, int limit) {
    long minute = System.currentTimeMillis() / 60000;
    var old = buckets.get(key);
    if (old == null || old.minute != minute) {
      if (buckets.size() >= 10000) buckets.entrySet().removeIf(e -> e.getValue().minute < minute);
      if (buckets.size() >= 10000) return false;
      buckets.put(key, new Bucket(minute, 1));
      return true;
    }
    if (old.count >= limit) return false;
    buckets.put(key, new Bucket(minute, old.count + 1));
    return true;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long started = System.nanoTime();
    String path = request.getRequestURI();
    String requestId = UUID.randomUUID().toString();
    response.setHeader("X-Request-ID", requestId);
    MDC.put("requestId", requestId);
    try {
      boolean mutation = Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.getMethod());
      if (path.startsWith("/api/v1") && !path.endsWith("/telegram/webhook")) {
        var authentication =
            org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication();
        String identity =
            authentication == null || !authentication.isAuthenticated()
                ? request.getRemoteAddr()
                : authentication.getName();
        String category = path.endsWith("/auth/telegram") ? "auth" : mutation ? "write" : "read";
        if (!allowed(
            category + ":" + identity, category.equals("auth") ? 120 : mutation ? 120 : 1200)) {
          response.setHeader("Retry-After", "60");
          problem(response, 429, "Too many requests. Try again shortly.");
          return;
        }
      }
      if (mutation) {
        if (request.getContentLengthLong() > 131072) {
          problem(response, 413, "Request body too large.");
          return;
        }
        byte[] body = request.getInputStream().readNBytes(131073);
        if (body.length > 131072) {
          problem(response, 413, "Request body too large.");
          return;
        }
        var wrapped =
            new HttpServletRequestWrapper(request) {
              @Override
              public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                  public int read() {
                    return input.read();
                  }

                  public boolean isFinished() {
                    return input.available() == 0;
                  }

                  public boolean isReady() {
                    return true;
                  }

                  public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                  }
                };
              }

              @Override
              public BufferedReader getReader() {
                return new BufferedReader(
                    new InputStreamReader(
                        getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
              }
            };
        chain.doFilter(wrapped, response);
      } else chain.doFilter(request, response);
    } finally {
      var route =
          request.getAttribute(
              org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      var variables =
          request.getAttribute(
              org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
      Object eventId = variables instanceof Map<?, ?> map ? map.get("id") : null;
      String safeId =
          eventId instanceof String value && value.matches("[0-9a-fA-F-]{36}") ? value : "-";
      // Only the framework route template and validated UUID enter logs, never query/body/headers.
      log.info(
          "request_complete requestId={} resourceId={} operation={} route={} status={} durationMs={}",
          requestId,
          safeId,
          request.getMethod(),
          route == null ? "unmapped" : route,
          response.getStatus(),
          (System.nanoTime() - started) / 1_000_000);
      MDC.remove("requestId");
    }
  }

  private void problem(HttpServletResponse response, int status, String detail) throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response
        .getWriter()
        .write(
            "{\"status\":"
                + status
                + ",\"title\":\"Request rejected\",\"detail\":\""
                + detail
                + "\"}");
  }
}
