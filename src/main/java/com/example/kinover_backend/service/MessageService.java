// src/main/java/com/example/kinover_backend/service/MessageService.java
package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.Message;
import com.example.kinover_backend.enums.MessageType;
import com.example.kinover_backend.repository.ChatRoomRepository;
import com.example.kinover_backend.repository.MessageRepository;
import com.example.kinover_backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final ChatRoomService chatRoomService;
    private final FcmNotificationService fcmNotificationService;

    private final ChannelTopic channelTopic = new ChannelTopic("chat:messages");

    @Value("${cloudfront.domain}")
    private String cloudFrontDomain;

    @Transactional
    public void addMessage(MessageDTO dto) {
        var chatRoom = chatRoomRepository.findById(dto.getChatRoomId())
                .orElseThrow(() -> new IllegalArgumentException("ChatRoom not found"));

        var sender = userRepository.findById(dto.getSenderId())
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        Message message = new Message();
        message.setMessageId(UUID.randomUUID());
        message.setChatRoom(chatRoom);
        message.setSender(sender);
        message.setMessageType(dto.getMessageType());

        // ✅ 이미지/비디오 content 처리
        if (dto.getMessageType() == MessageType.image || dto.getMessageType() == MessageType.video) {
            List<String> fileNames = dto.getImageUrls();
            if (fileNames == null || fileNames.isEmpty()) {
                throw new RuntimeException("이미지 파일명이 비어 있습니다.");
            }

            List<String> imageUrls = fileNames.stream()
                    .map(fileName -> fileName.startsWith("http") ? fileName : cloudFrontDomain + fileName)
                    .collect(Collectors.toList());

            message.setContent(String.join(",", imageUrls));
        } else {
            message.setContent(dto.getContent());
        }

        Message saved = messageRepository.save(message);

        // ✅ 보낸 사람은 자동 읽음 처리 (내가 보낸 건 내가 읽은 것)
        chatRoomService.markRead(saved.getChatRoom().getChatRoomId(), saved.getSender().getUserId(), saved.getCreatedAt());

        // ✅ Redis 발행
        try {
            MessageDTO responseDto = getMessageDTO(saved);
            String json = objectMapper.writeValueAsString(responseDto);
            redisTemplate.convertAndSend(channelTopic.getTopic(), json);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Redis 발행 중 오류", e);
        }

        // ✅ FCM 알림 (멘션 분기)
        sendChatPushNotifications(dto);
    }

    private void sendChatPushNotifications(MessageDTO dto) {
        List<UserDTO> users = chatRoomService.getUsersByChatRoom(dto.getChatRoomId());

        Set<Long> mentionTargets = Optional.ofNullable(dto.getMentionUserIds())
                .orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.equals(dto.getSenderId()))
                .collect(Collectors.toSet());

        // 1) 멘션 대상자: 무조건 멘션 알림
        for (UserDTO u : users) {
            Long userId = u.getUserId();
            if (mentionTargets.contains(userId)) {
                fcmNotificationService.sendMentionChatNotification(userId, dto);
            }
        }

        // 2) 나머지: 설정 ON일 때만
        for (UserDTO u : users) {
            Long userId = u.getUserId();

            if (userId.equals(dto.getSenderId())) continue;
            if (mentionTargets.contains(userId)) continue;

            if (fcmNotificationService.isChatRoomNotificationOn(userId, dto.getChatRoomId())) {
                fcmNotificationService.sendChatNotification(userId, dto);
            }
        }
    }

    @NotNull
    private static MessageDTO getMessageDTO(Message saved) {
        MessageDTO responseDto = new MessageDTO();
        responseDto.setMessageId(saved.getMessageId());
        responseDto.setChatRoomId(saved.getChatRoom().getChatRoomId());
        responseDto.setSenderId(saved.getSender().getUserId());
        responseDto.setSenderName(saved.getSender().getName());
        responseDto.setSenderImage(saved.getSender().getImage());
        responseDto.setMessageType(saved.getMessageType());
        responseDto.setCreatedAt(saved.getCreatedAt());

        if (saved.getMessageType() == MessageType.image || saved.getMessageType() == MessageType.video) {
            if (saved.getContent() != null && !saved.getContent().isEmpty()) {
                responseDto.setImageUrls(List.of(saved.getContent().split(",")));
            } else {
                responseDto.setImageUrls(List.of());
            }
            responseDto.setContent(null);
        } else {
            responseDto.setContent(saved.getContent());
            responseDto.setImageUrls(null);
        }

        responseDto.setMentionUserIds(null);
        return responseDto;
    }

    public List<MessageDTO> fetchMessagesBefore(UUID chatRoomId, LocalDateTime before, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        LocalDateTime beforeTime = (before != null) ? before : LocalDateTime.now();

        List<Message> messages = new ArrayList<>(
                messageRepository.findByChatRoom_ChatRoomIdAndCreatedAtBefore(chatRoomId, beforeTime, pageRequest)
                        .getContent()
        );

        Collections.reverse(messages);

        return messages.stream()
                .map(MessageService::getMessageDTO)
                .toList();
    }
}
