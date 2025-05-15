package com.example.kinover_backend.redis;

import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.service.ChatRoomService;
import com.example.kinover_backend.service.FcmNotificationService;
import com.example.kinover_backend.websocket.WebSocketMessageHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Set;

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
                        System.out.println("[Redis → WebSocket 전송] to userId=" + userId + ", sessionId=" + session.getId());
                    }else {
                        // WebSocket 미연결 시 FCM 발송 조건 체크
                        if (fcmNotificationService.isNotificationOn(userId, messageDTO.getChatRoomId())) {
                            fcmNotificationService.sendChatNotification(userId, messageDTO);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[ChatMessageSubscriber 오류] " + e.getMessage());
        }
    }
}