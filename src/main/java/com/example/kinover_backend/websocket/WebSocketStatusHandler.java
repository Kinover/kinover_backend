package com.example.kinover_backend.websocket;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebSocketStatusHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    private final UserService userService;

    // 사용자별 다중 세션 관리
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final Map<String, UUID> sessionIdToFamilyId = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = extractUserId(session);
        UUID familyId = extractFamilyId(session);
        sessionIdToFamilyId.put(session.getId(), familyId);

        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        userService.updateUserOnlineStatus(userId, familyId, true);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = extractUserId(session);
        UUID familyId = sessionIdToFamilyId.get(session.getId());

        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                userService.updateUserOnlineStatus(userId, familyId, false);
            }
        }
        sessionIdToFamilyId.remove(session.getId());
    }

    private Long extractUserId(WebSocketSession session) {
        URI uri = session.getUri();
        String token = getQueryParam(uri, "token");
        return Long.parseLong(jwtUtil.parseToken(token).getSubject());
    }

    private UUID extractFamilyId(WebSocketSession session) {
        URI uri = session.getUri();
        String fid = getQueryParam(uri, "familyId");
        return UUID.fromString(fid);
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
