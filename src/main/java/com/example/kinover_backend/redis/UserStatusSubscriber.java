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
            String channel = new String(message.getChannel()); // 실제 발행된 채널명
            System.out.println("Redis channel: " + channel);
            System.out.println("Subscribed pattern: " + new String(pattern)); // 항상 family:status:*

            List<UserStatusDTO> statusList = objectMapper.readValue(
                    json, new TypeReference<List<UserStatusDTO>>() {});
            if (statusList.isEmpty()) {
                System.out.println("statusList 비어있음 → return");
                return;
            }

            UUID familyId = extractFamilyIdFromChannel(channel);
            System.out.println("추출된 familyId: " + familyId);
            if (familyId == null) {
                System.out.println("familyId == null → return");
                return;
            }

            Set<WebSocketSession> sessions = webSocketFamilyStatusHandler.getSessionsByFamilyId(familyId);
            System.out.println("세션 개수: " + sessions.size());
            if (sessions.isEmpty()) {
                System.out.println("세션 없음 → return");
                return;
            }

            TextMessage messageToSend = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    System.out.println("세션에 메시지 전송: " + session.getId());
                    session.sendMessage(messageToSend);
                } else {
                    System.out.println("세션 닫힘: " + session.getId());
                }
            }

            System.out.println("onMessage 정상 종료");
        } catch (Exception e) {
            System.err.println("UserStatusSubscriber failed: " + e.getMessage());
            e.printStackTrace();
        }
    }



    private UUID extractFamilyIdFromChannel(String channel) {
        try {
            // channel 예: "family:status:<uuid>"
            String[] parts = channel.split(":");
            if (parts.length == 3) {
                return UUID.fromString(parts[2]);
            }
            System.err.println("Invalid channel format: " + channel);
        } catch (Exception e) {
            System.err.println("Invalid channel format: " + channel + " / " + e.getMessage());
        }
        return null;
    }


}
