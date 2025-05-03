package com.example.kinover_backend.websocket;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.example.kinover_backend.service.OpenAiService;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebSocketMessageHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    // 사용자별 다중 세션 지원 (Set 사용)
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = extractUserIdFromSession(session);
        if (userId != null) {
            sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
            System.out.println("[WebSocket 연결 성공] userId=" + userId + ", sessionId=" + session.getId());
        } else {
            System.out.println("[WebSocket 연결 실패] JWT 인증 실패");
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = extractUserIdFromSession(session);
        if (userId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // 사용자 메시지 수신
        MessageDTO dto = objectMapper.readValue(message.getPayload(), MessageDTO.class);

        if (dto.getSender() == null || !userId.equals(dto.getSender().getUserId())) {
            System.out.println("[WebSocket] sender ID 불일치: JWT=" + userId + ", DTO=" + dto.getSender().getUserId());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // 메시지 저장 (Redis 발행 포함)
        messageService.addMessage(dto);

        // 만약 이 채팅방이 Kino 채팅방이라면 kino 응답 생성 후 키노 응답도 addMessage 해줌.
        if (dto.getChatRoom() != null && dto.getChatRoom().isKino()) {
            String reply = openAiService.getKinoResponse(dto.getChatRoom().getChatRoomId());

            MessageDTO kinoReply = new MessageDTO();
            kinoReply.setMessageId(UUID.randomUUID());
            kinoReply.setContent(reply);
            kinoReply.setCreatedAt(LocalDateTime.now());
            kinoReply.setChatRoom(dto.getChatRoom());
            kinoReply.setMessageType(MessageType.text);

            // Kino 사용자 설정 (DB에 이미 userId=9999999999로 존재함)
            User kinoUser = userService.getUserById(9999999999L);
            kinoReply.setSender(new UserDTO(kinoUser));

            messageService.addMessage(kinoReply);
        }
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = extractUserIdFromSession(session);
        if (userId != null) {
            Set<WebSocketSession> userSessions = sessions.get(userId);
            if (userSessions != null) {
                userSessions.remove(session);
                if (userSessions.isEmpty()) {
                    sessions.remove(userId);
                }
            }
            System.out.println("[WebSocket 연결 종료] userId=" + userId + ", sessionId=" + session.getId());
        }
    }

    public Set<WebSocketSession> getSessionsByUserId(Long userId) {
        return sessions.getOrDefault(userId, Set.of());
    }

    private Long extractUserIdFromSession(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null) return null;
            String query = uri.getQuery();
            if (query == null || !query.contains("token=")) return null;

            String token = query.substring(query.indexOf("token=") + 6);
            if (token.contains("&")) {
                token = token.substring(0, token.indexOf("&"));
            }
            return jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            return null;
        }
    }
}
