package com.gather.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {
  private final GatherWebSocketHandler handler;
  private final String origin;

  public WebSocketConfiguration(
      GatherWebSocketHandler handler, @Value("${gather.frontend-origin}") String origin) {
    this.handler = handler;
    this.origin = origin;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws").setAllowedOrigins(origin);
  }
}
