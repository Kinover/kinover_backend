package com.example.kinover_backend.redis;

import com.example.kinover_backend.dto.UserStatusDTO;
import com.example.kinover_backend.websocket.WebSocketFamilyStatusHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserStatusSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final WebSocketFamilyStatusHandler webSocketFamilyStatusHandler;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            List<UserStatusDTO> statusList = objectMapper.readValue(
                    json, new TypeReference<List<UserStatusDTO>>() {});

            if (statusList.isEmpty()) return;

            UUID familyId = extractFamilyIdFromChannel(pattern);
            if (familyId == null) return;

            // 화면에 접속해 있는 유저 세션만 가져옴
            Set<WebSocketSession> sessions = webSocketFamilyStatusHandler.getSessionsByFamilyId(familyId);
            if (sessions.isEmpty()) return;

            TextMessage messageToSend = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(messageToSend);
                }
            }

        } catch (Exception e) {
            System.err.println("UserStatusSubscriber failed: " + e.getMessage());
        }
    }

    private UUID extractFamilyIdFromChannel(byte[] pattern) {
        try {
            String channel = new String(pattern); // e.g., family:status:UUID
            String[] parts = channel.split(":");
            if (parts.length == 3) {
                return UUID.fromString(parts[2]);
            }
        } catch (Exception e) {
            System.err.println("Invalid channel format");
        }
        return null;
    }
}
