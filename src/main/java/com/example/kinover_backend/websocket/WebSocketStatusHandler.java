package com.example.kinover_backend.websocket;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@RequiredArgsConstructor
public class WebSocketStatusHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    private final UserService userService;

    // 사용자별 다중 세션 관리
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        URI uri = session.getUri();
        System.out.println("[WS /status] 연결 요청: " + uri);

        String token = validateToken(session); // jwt 토큰 유효성 검사
        Long userId = jwtUtil.getUserIdFromToken(token);

        System.out.println("[WS /status] userId 파싱 성공: " + userId);

        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        userService.updateUserOnlineStatus(userId, true);  // ✅ 수정된 메서드 사용

        System.out.println("[WS /status] 세션 등록 및 온라인 처리 완료");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String token = getQueryParam(Objects.requireNonNull(session.getUri()), "token");
        Long userId = jwtUtil.getUserIdFromToken(token);

        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                userService.updateUserOnlineStatus(userId, false); // ✅ 수정된 메서드 사용
            }
        }
    }

    private String validateToken(WebSocketSession session) {
        URI uri = session.getUri();
        String token = getQueryParam(uri, "token");
        System.out.println("[WS] 받은 토큰: " + token);

        if (token == null || !jwtUtil.isTokenValid(token)) {
            throw new RuntimeException("Invalid or expired JWT token");
        }

        return token;
    }

    private String getQueryParam(URI uri, String key) {
        String query = uri.getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2 && pair[0].equals(key)) {
                return pair[1];
            }
        }
        return null;
    }
}