package com.example.kinover_backend.websocket;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.service.MessageService;
import com.example.kinover_backend.service.OpenAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.example.kinover_backend.enums.MessageType;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.service.UserService;


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
    private final OpenAiService openAiService;
    private final UserService userService;

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

        // [1] WebSocket 메시지 원문 출력
        System.out.println("[DEBUG] ▶ Raw WebSocket message: " + message.getPayload());

        // [2] JSON 파싱 (DTO 변환)
        MessageDTO dto = objectMapper.readValue(message.getPayload(), MessageDTO.class);

        // [3] DTO 전체 구조 출력 (kino 포함 여부 확인)
        System.out.println("[DEBUG] ▶ Parsed MessageDTO: " + objectMapper.writeValueAsString(dto));

        // [4] sender 유효성 체크
        if (dto.getSender() == null || !userId.equals(dto.getSender().getUserId())) {
            System.out.println("[WebSocket] sender ID 불일치: JWT=" + userId + ", DTO=" + dto.getSender().getUserId());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // [5] 메시지 저장
        messageService.addMessage(dto);

        // [6] chatRoom 및 isKino 확인
        System.out.printf("[DEBUG] ▶ chatRoom=%s, isKino=%s%n",
                dto.getChatRoom() == null ? "null" : dto.getChatRoom().getChatRoomId(),
                dto.getChatRoom() != null ? dto.getChatRoom().isKino() : "N/A"
        );

        // [7] Kino 처리
        if (dto.getChatRoom() != null && dto.getChatRoom().isKino()) {
            String reply = openAiService.getKinoResponse(dto.getChatRoom().getChatRoomId());

            MessageDTO kinoReply = new MessageDTO();
            kinoReply.setMessageId(UUID.randomUUID());
            kinoReply.setContent(reply);
            kinoReply.setChatRoom(dto.getChatRoom());
            kinoReply.setMessageType(MessageType.text);

            UserDTO kinoUser = userService.getUserById(9999999999L);
            kinoReply.setSender(kinoUser);

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
