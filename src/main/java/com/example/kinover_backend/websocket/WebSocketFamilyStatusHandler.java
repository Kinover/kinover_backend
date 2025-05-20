package com.example.kinover_backend.websocket;

import com.example.kinover_backend.JwtUtil;
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
public class WebSocketFamilyStatusHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;

    // familyId → 세션들
    private final Map<UUID, Set<WebSocketSession>> familySessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {

        URI uri = session.getUri();
        System.out.println("[WS /family-status] 연결 요청 URI: " + uri);

        UUID familyId = extractFamilyId(session);
        familySessions.computeIfAbsent(familyId, f -> new CopyOnWriteArraySet<>()).add(session);

        System.out.println("[WS /family-status] 연결 완료. familyId: " + familyId +
                ", 현재 세션 수: " + familySessions.get(familyId).size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID familyId = extractFamilyId(session);
        Set<WebSocketSession> sessions = familySessions.get(familyId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                familySessions.remove(familyId);
            }
        }
    }

    public Set<WebSocketSession> getSessionsByFamilyId(UUID familyId) {
        return familySessions.getOrDefault(familyId, Collections.emptySet());
    }

    private UUID extractFamilyId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String query = uri.getQuery();
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2 && pair[0].equals("familyId")) {
                return UUID.fromString(pair[1]);
            }
        }
        return null;
    }
}
