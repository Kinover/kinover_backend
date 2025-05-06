package com.example.kinover_backend.config;

import com.example.kinover_backend.websocket.WebSocketMessageHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketMessageHandler webSocketMessageHandler;

    public WebSocketConfig(WebSocketMessageHandler webSocketMessageHandler) {
        this.webSocketMessageHandler = webSocketMessageHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 웹소켓 핸들러 등록
        registry.addHandler(webSocketMessageHandler, "/chat")
                .setAllowedOrigins("*");  // 클라이언트의 출처 허용
    }
}