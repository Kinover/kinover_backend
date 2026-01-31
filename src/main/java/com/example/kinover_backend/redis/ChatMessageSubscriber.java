package com.example.kinover_backend.redis;

import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.service.ChatRoomService;
import com.example.kinover_backend.service.FcmNotificationService;
import com.example.kinover_backend.websocket.WebSocketMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
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
public class ChatMessageSubscriber implements MessageListener {

    private final WebSocketMessageHandler webSocketMessageHandler;
    private final ChatRoomService chatRoomService;
    private final ObjectMapper objectMapper;
    private final FcmNotificationService fcmNotificationService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // Redis로부터 수신한 메시지를 MessageDTO로 역직렬화
            String json = new String(message.getBody());

            // 1. 먼저 트리 형태로 읽어서 'type' 필드가 있는지 확인
            JsonNode jsonNode = objectMapper.readTree(json);
            
            // 2. 만약 읽음 이벤트(room:read)라면?
            if (jsonNode.has("type") && "room:read".equals(jsonNode.get("type").asText())) {
                
                // "읽음 처리"는 채팅방에 있는 사람들에게 실시간 전송만 하면 됨 (FCM 불필요)
                String chatRoomIdStr = jsonNode.get("chatRoomId").asText();
                UUID chatRoomId = UUID.fromString(chatRoomIdStr);

                List<UserDTO> participants = chatRoomService.getUsersByChatRoom(chatRoomId);

                for (UserDTO user : participants) {
                    Long userId = user.getUserId();
                    Set<WebSocketSession> sessions = webSocketMessageHandler.getSessionsByUserId(userId);
                    for (WebSocketSession session : sessions) {
                        if (session != null && session.isOpen()) {
                            // 그대로 클라이언트에게 전달 (클라이언트가 type: room:read를 보고 처리)
                            session.sendMessage(new TextMessage(json));
                        }
                    }
                }
                // 읽음 이벤트는 여기서 종료
                return;
            }

            MessageDTO messageDTO = objectMapper.readValue(json, MessageDTO.class);

            // 채팅방 ID로 참여자 목록 조회
            List<UserDTO> participants = chatRoomService.getUsersByChatRoom(
                    messageDTO.getChatRoomId()
            );

            for (UserDTO user : participants) {
                Long userId = user.getUserId();
                Set<WebSocketSession> sessions = webSocketMessageHandler.getSessionsByUserId(userId);

                for (WebSocketSession session : sessions) {
                    if (session != null && session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                        System.out.println("[Redis → WebSocket 전송] to userId=" + userId
                                + ", sessionId=" + session.getId());
                    }
                }
                if (sessions.isEmpty()) {
                        // WebSocket 미연결 시 FCM 발송 조건 체크
                        System.out.println("[WebSocket 닫힘 → FCM 후보] userId=" + userId);
                        if (fcmNotificationService.isChatRoomNotificationOn(userId, messageDTO.getChatRoomId())) {
                            System.out.println("[FCM 전송] to userId=" + userId);
                            fcmNotificationService.sendChatNotification(userId, messageDTO);
                        } else {
                            System.out.println("[FCM 전송 안 함 - 알림 OFF] userId=" + userId);
                        }
                    }
            }
        } catch (Exception e) {
            System.out.println("[ChatMessageSubscriber 오류] " + e.getMessage());
        }
    }
}