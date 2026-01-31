// src/main/java/com/example/kinover_backend/websocket/WebSocketMessageHandler.java
package com.example.kinover_backend.websocket;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.dto.ReadWsRequestDTO;
import com.example.kinover_backend.enums.MessageType;
import com.example.kinover_backend.service.ChatRoomService;
import com.example.kinover_backend.service.MessageService;
import com.example.kinover_backend.service.OpenAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
// import lombok.RequiredArgsConstructor; // 제거
import org.springframework.context.annotation.Lazy; // 추가
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
// @RequiredArgsConstructor // 제거: 직접 생성자를 만들어 @Lazy를 적용하기 위함
public class WebSocketMessageHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;
    private final OpenAiService openAiService;
    private final ChatRoomService chatRoomService;
    private final StringRedisTemplate redisTemplate;
    private final ChannelTopic channelTopic;

    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    // ✅ 생성자 직접 주입 (Redis 관련 빈에 @Lazy 적용)
    public WebSocketMessageHandler(
            JwtUtil jwtUtil,
            MessageService messageService,
            ObjectMapper objectMapper,
            OpenAiService openAiService,
            ChatRoomService chatRoomService,
            @Lazy StringRedisTemplate redisTemplate, // 순환 참조 끊기
            @Lazy ChannelTopic channelTopic          // 순환 참조 끊기
    ) {
        this.jwtUtil = jwtUtil;
        this.messageService = messageService;
        this.objectMapper = objectMapper;
        this.openAiService = openAiService;
        this.chatRoomService = chatRoomService;
        this.redisTemplate = redisTemplate;
        this.channelTopic = channelTopic;
    }

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

            sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
            System.out.println("[WS CONNECT] userId=" + userId + ", sessionId=" + session.getId());
        } catch (Exception e) {
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
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

        Map<String, Object> map = objectMapper.readValue(rawPayload, Map.class);
        String type = map.get("type") != null ? String.valueOf(map.get("type")) : "message:new";

        // =========================
        // A) 읽음 이벤트
        // =========================
        if ("room:read".equals(type)) {
            ReadWsRequestDTO dto = objectMapper.readValue(rawPayload, ReadWsRequestDTO.class);

            if (dto.getChatRoomId() == null || dto.getLastReadAt() == null) {
                session.close(CloseStatus.BAD_DATA);
                return;
            }

            if (!chatRoomService.isMember(dto.getChatRoomId(), userId)) {
                session.close(CloseStatus.NOT_ACCEPTABLE);
                return;
            }

            String jsonPayload = makeReadBroadcastPayload(dto.getChatRoomId(), userId, dto.getLastReadAt());
            // @Lazy로 주입된 redisTemplate 사용 시점에 실제 빈이 로드됨
            redisTemplate.convertAndSend(channelTopic.getTopic(), jsonPayload);

            return;
        }

        // =========================
        // B) 메시지 이벤트
        // =========================
        MessageDTO dto = objectMapper.readValue(rawPayload, MessageDTO.class);

        if (dto.getSenderId() == null || !userId.equals(dto.getSenderId())) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        if (dto.getChatRoomId() == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        if (!chatRoomService.isMember(dto.getChatRoomId(), userId)) {
            System.out.println("[WS DENY] not a member. userId=" + userId + ", chatRoomId=" + dto.getChatRoomId());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        dto.setMessageId(null);

        messageService.addMessage(dto);

        LocalDateTime lastReadAt = LocalDateTime.now();
        boolean updated = chatRoomService.markRead(dto.getChatRoomId(), userId, lastReadAt);

        if (updated) {
            broadcastToRoomMembers(
                    dto.getChatRoomId(),
                    makeReadBroadcastPayload(dto.getChatRoomId(), userId, lastReadAt)
            );
        }

        if (chatRoomService.isKinoRoom(dto.getChatRoomId())) {
            String reply = openAiService.getKinoResponse(dto.getChatRoomId(), userId);

            MessageDTO kinoReply = new MessageDTO();
            kinoReply.setMessageId(UUID.randomUUID());
            kinoReply.setChatRoomId(dto.getChatRoomId());
            kinoReply.setContent(reply);
            kinoReply.setMessageType(MessageType.text);
            kinoReply.setSenderId(9999999999L);

            messageService.addMessage(kinoReply);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        URI uri = session.getUri();
        if (uri == null) return;

        String token = getQueryParam(uri, "token");
        if (token == null) return;

        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) return;

        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) sessions.remove(userId);
        }

        System.out.println("[WS CLOSE] userId=" + userId + ", sessionId=" + session.getId());
    }

    public Set<WebSocketSession> getSessionsByUserId(Long userId) {
        return sessions.getOrDefault(userId, Set.of());
    }

    private String getQueryParam(URI uri, String key) {
        String query = uri.getQuery();
        if (query == null) return null;

        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && pair[0].equals(key)) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void broadcastToRoomMembers(UUID chatRoomId, String payload) {
        List<Long> memberIds = chatRoomService.getMemberIds(chatRoomId);
        for (Long memberId : memberIds) {
            Set<WebSocketSession> ss = getSessionsByUserId(memberId);
            for (WebSocketSession s : ss) {
                try {
                    if (s.isOpen()) s.sendMessage(new TextMessage(payload));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String makeReadBroadcastPayload(UUID chatRoomId, Long userId, LocalDateTime lastReadAt) throws Exception {
        Map<String, Object> out = new HashMap<>();
        out.put("type", "room:read");
        out.put("chatRoomId", chatRoomId);
        out.put("userId", userId);
        out.put("lastReadAt", lastReadAt);
        return objectMapper.writeValueAsString(out);
    }
}