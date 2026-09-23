package com.gather.common;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfiguration {
  private static void securityProblem(jakarta.servlet.http.HttpServletResponse response, int status)
      throws java.io.IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"status\":"
                + status
                + ",\"title\":\""
                + (status == 401 ? "Authentication required" : "Access denied")
                + "\"}");
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  SecretKeySpec signingKey(@Value("${gather.jwt-secret}") String secret) {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalStateException("JWT_SIGNING_SECRET must contain at least 32 bytes.");
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKeySpec key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKeySpec key) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(new JwtTimestampValidator(java.time.Duration.ZERO));
    return decoder;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${gather.frontend-origin}") String origin) {
    var config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(origin));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "If-Match"));
    config.setExposedHeaders(List.of("ETag"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, @Value("${gather.management-token:}") String managementToken)
      throws Exception {
    return http.csrf(c -> c.disable())
        .cors(c -> {})
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/ws",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/api/v1/auth/telegram",
                        "/api/v1/telegram/webhook",
                        "/api/openapi.json",
                        "/api/openapi.json/**")
                    .permitAll()
                    .requestMatchers("/actuator/metrics", "/actuator/metrics/**")
                    .access(
                        (authentication, context) -> {
                          var supplied = context.getRequest().getHeader("X-Management-Token");
                          return new org.springframework.security.authorization
                              .AuthorizationDecision(
                              managementToken.length() >= 32
                                  && supplied != null
                                  && java.security.MessageDigest.isEqual(
                                      managementToken.getBytes(
                                          java.nio.charset.StandardCharsets.UTF_8),
                                      supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                        })
                    .requestMatchers("/actuator/**")
                    .denyAll()
                    .anyRequest()
                    .authenticated())
        .addFilterAfter(
            new RequestLimits(),
            org.springframework.security.oauth2.server.resource.web.authentication
                .BearerTokenAuthenticationFilter.class)
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (request, response, error) -> securityProblem(response, 401))
                    .accessDeniedHandler(
                        (request, response, error) -> securityProblem(response, 403)))
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> {})
                    .authenticationEntryPoint(
                        (request, response, error) -> securityProblem(response, 401))
                    .accessDeniedHandler(
                        (request, response, error) -> securityProblem(response, 403)))
        .build();
  }
}
