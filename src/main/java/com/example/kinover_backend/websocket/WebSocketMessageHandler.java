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
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class WebSocketMessageHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;
    private final OpenAiService openAiService;
    private final ChatRoomService chatRoomService;

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
        // A) 읽음 이벤트 (클라이언트가 "방을 실제로 봤다"는 신호)
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

            boolean updated = chatRoomService.markRead(dto.getChatRoomId(), userId, dto.getLastReadAt());

            // ✅ 실제로 forward 업데이트 된 경우에만 브로드캐스트
            if (updated) {
                broadcastToRoomMembers(
                        dto.getChatRoomId(),
                        makeReadBroadcastPayload(dto.getChatRoomId(), userId, dto.getLastReadAt())
                );
            }
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

        // 서버에서 messageId는 새로 생성되도록 강제
        dto.setMessageId(null);

        // 1) 메시지 저장
        messageService.addMessage(dto);

        // 2) ✅ "보낸 사람(sender)"은 본인이 보낸 메시지를 '읽음'으로 간주해도 되므로
        //    서버에서 자동 read 처리 (프론트에서 -1 같은 트릭을 할 필요가 없어짐)
        //
        //    - 가장 좋은 건 "저장된 메시지 createdAt"으로 markRead 하는 것인데,
        //      현재 addMessage(dto)가 반환값이 없어 여기서는 LocalDateTime.now()로 처리.
        //    - Kino 자동응답은 아직 user가 안 읽었을 수 있으니, user 메시지 직후까지만 read를 올림.
        LocalDateTime lastReadAt = LocalDateTime.now();
        boolean updated = chatRoomService.markRead(dto.getChatRoomId(), userId, lastReadAt);

        if (updated) {
            broadcastToRoomMembers(
                    dto.getChatRoomId(),
                    makeReadBroadcastPayload(dto.getChatRoomId(), userId, lastReadAt)
            );
        }

        // 3) 키노룸이면 키노 답장 저장 (여기서는 read 올리지 않음)
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
