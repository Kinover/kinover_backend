package com.example.kinover_backend.websocket;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.enums.MessageType;
import com.example.kinover_backend.service.ChatRoomService;
import com.example.kinover_backend.service.MessageService;
import com.example.kinover_backend.service.OpenAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@RequiredArgsConstructor
public class WebSocketMessageHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;
    private final OpenAiService openAiService;
    private final ChatRoomService chatRoomService;

    // userId -> sessions
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null) {
                session.close(CloseStatus.BAD_DATA);
                return;
            }

            String token = getQueryParam(uri, "token");
            if (token == null || !jwtUtil.isTokenValid(token)) {
                session.close(CloseStatus.NOT_ACCEPTABLE);
                return;
            }

            Long userId = jwtUtil.getUserIdFromToken(token);
            if (userId == null) {
                session.close(CloseStatus.NOT_ACCEPTABLE);
                return;
            }

            sessions
                    .computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>())
                    .add(session);

            System.out.println("[WS CONNECT] userId=" + userId + ", sessionId=" + session.getId());
        } catch (Exception e) {
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (Exception ignored) {
            }
        }
    }

    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        URI uri = session.getUri();
        if (uri == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        String token = getQueryParam(uri, "token");
        if (token == null || !jwtUtil.isTokenValid(token)) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        String rawPayload = message.getPayload();
        MessageDTO dto = objectMapper.readValue(rawPayload, MessageDTO.class);

        // sender 검증
        if (dto.getSenderId() == null || !userId.equals(dto.getSenderId())) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // chatRoomId 검증
        if (dto.getChatRoomId() == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // ✅ 권한 체크 (핵심)
        if (!chatRoomService.isMember(dto.getChatRoomId(), userId)) {
            System.out.println("[WS DENY] not a member. userId=" + userId + ", chatRoomId=" + dto.getChatRoomId());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // merge 방지
        dto.setMessageId(null);

        // 메시지 저장 + 브로드캐스트
        messageService.addMessage(dto);

        // Kino 방이면 AI 응답
        if (chatRoomService.isKinoRoom(dto.getChatRoomId())) {
            String reply = openAiService.getKinoResponse(dto.getChatRoomId(), userId);

            MessageDTO kinoReply = new MessageDTO();
            kinoReply.setMessageId(UUID.randomUUID());
            kinoReply.setChatRoomId(dto.getChatRoomId());
            kinoReply.setContent(reply);
            kinoReply.setMessageType(MessageType.text);
            kinoReply.setSenderId(9999999999L); // Kino

            messageService.addMessage(kinoReply);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        URI uri = session.getUri();
        if (uri == null)
            return;

        String token = getQueryParam(uri, "token");
        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null)
            return;

        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                sessions.remove(userId);
            }
        }

        System.out.println("[WS CLOSE] userId=" + userId + ", sessionId=" + session.getId());
    }

    public Set<WebSocketSession> getSessionsByUserId(Long userId) {
        return sessions.getOrDefault(userId, Set.of());
    }

    private String getQueryParam(URI uri, String key) {
        String query = uri.getQuery();
        if (query == null)
            return null;

        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && pair[0].equals(key)) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
