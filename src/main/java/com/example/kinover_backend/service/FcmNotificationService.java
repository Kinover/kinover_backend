package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.entity.ChatRoom;
import com.example.kinover_backend.entity.ChatRoomNotificationSetting;
import com.example.kinover_backend.entity.FcmToken;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.repository.ChatRoomNotificationRepository;
import com.example.kinover_backend.repository.ChatRoomRepository;
import com.example.kinover_backend.repository.FcmTokenRepository;
import com.example.kinover_backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FcmNotificationService {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomNotificationRepository notificationRepository;
    private final FcmTokenRepository fcmTokenRepository;

    public boolean isNotificationOn(Long userId, UUID chatRoomId) {
        User user = userRepository.findById(userId).orElseThrow();
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId).orElseThrow();

        return notificationRepository.findByUserAndChatRoom(user, chatRoom)
                .map(ChatRoomNotificationSetting::isNotificationOn)
                .orElse(true); // 설정 없으면 알림 ON
    }

    public void sendChatNotification(Long userId, MessageDTO messageDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        ChatRoom chatRoom = chatRoomRepository.findById(messageDTO.getChatRoomId())
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다."));
        Optional<FcmToken> fcmTokenOpt = fcmTokenRepository.findByUser(user);

        if (fcmTokenOpt.isEmpty()) return;

        String token = fcmTokenOpt.get().getToken();
        String messageType = messageDTO.getMessageType().name();

        String body;

        switch (messageType) {
            case "text" -> {
                body = messageDTO.getSenderName() + ": " + messageDTO.getContent();
            }
            case "image" -> {
                int imageCount = messageDTO.getImageUrls() != null ? messageDTO.getImageUrls().size() : 0;
                body = messageDTO.getSenderName() + ": 사진 " + imageCount + "장을 보냈습니다.";
            }
            case "video" -> {
                body = messageDTO.getSenderName() + ": 동영상을 보냈습니다.";
            }
            default -> {
                body = messageDTO.getSenderName() + ": 새로운 메시지가 도착했습니다.";
            }
        }

        Message.Builder messageBuilder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(chatRoom.getRoomName())
                        .setBody(body)
                        .build())
                .putData("messageType", messageType)
                .putData("senderName", messageDTO.getSenderName())
                .putData("senderImage", messageDTO.getSenderImage())
                .putData("roomName", chatRoom.getRoomName());

        // 이미지/비디오의 경우 이미지 URL 리스트를 JSON 문자열로 변환해 전달
        if (messageType.equals("image") || messageType.equals("video")) {
            try {
                String imageUrlJson = new ObjectMapper().writeValueAsString(messageDTO.getImageUrls());
                messageBuilder.putData("imageUrls", imageUrlJson);
            } catch (Exception e) {
                e.printStackTrace();  // JSON 변환 실패 시 무시
            }
        }

        try {
            FirebaseMessaging.getInstance().send(messageBuilder.build());
        } catch (FirebaseMessagingException e) {
            e.printStackTrace(); // TODO: 로깅 시스템으로 전환
        }
    }
}
