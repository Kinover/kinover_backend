// src/main/java/com/example/kinover_backend/service/MessageService.java
package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.Message;
import com.example.kinover_backend.enums.MessageType;
import com.example.kinover_backend.repository.ChatRoomRepository;
import com.example.kinover_backend.repository.MessageRepository;
import com.example.kinover_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

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
                throw new RuntimeException("이미지/비디오 파일명이 비어 있습니다.");
            }

            List<String> urls = fileNames.stream()
                    .map(fn -> fn.startsWith("http") ? fn : cloudFrontDomain + fn)
                    .collect(Collectors.toList());

            message.setContent(String.join(",", urls));
        } else {
            message.setContent(dto.getContent());
        }

        Message saved = messageRepository.save(message);

        // ✅ 저장된 값 기반 DTO (createdAt 포함) -> 이걸로 Redis + Push 통일
        MessageDTO responseDto = getMessageDTO(saved);

        // ✅ Redis 발행
        try {
            String json = objectMapper.writeValueAsString(responseDto);
            redisTemplate.convertAndSend(channelTopic.getTopic(), json);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Redis 발행 중 오류", e);
        }

        // ✅ Push (저장된 createdAt 기준으로 '읽음이면 스킵' 가능)
        sendChatPushNotifications(responseDto);
    }

    private void sendChatPushNotifications(MessageDTO messageDtoFromDb) {
        List<UserDTO> users = chatRoomService.getUsersByChatRoom(messageDtoFromDb.getChatRoomId());

        // ✅ 멘션 대상 set
        Set<Long> mentionTargets = Optional.ofNullable(messageDtoFromDb.getMentionUserIds())
                .orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.equals(messageDtoFromDb.getSenderId()))
                .collect(Collectors.toSet());

        // ✅ 수신자별 "이미 읽음이면 푸시 스킵" (카톡 느낌)
        // - 사용자가 지금 방에 들어와서 읽음 처리(room:read)가 이미 올라갔으면 푸시 안 가는 게 자연스러움
        for (UserDTO u : users) {
            Long receiverId = u.getUserId();
            if (receiverId.equals(messageDtoFromDb.getSenderId())) continue;

            // ✅ lastReadAt >= message.createdAt 이면 이미 읽은 상태로 간주 -> 푸시 생략
            LocalDateTime lastReadAt = chatRoomService.getLastReadAt(messageDtoFromDb.getChatRoomId(), receiverId);
            if (lastReadAt != null && messageDtoFromDb.getCreatedAt() != null
                    && !lastReadAt.isBefore(messageDtoFromDb.getCreatedAt())) {
                continue;
            }

            // ✅ 1) 멘션 대상자: 설정 무시하고 멘션 푸시
            if (mentionTargets.contains(receiverId)) {
                fcmNotificationService.sendMentionChatNotification(receiverId, messageDtoFromDb);
                continue;
            }

            // ✅ 2) 나머지: 설정 true일 때만 일반 푸시
            if (fcmNotificationService.isChatRoomNotificationOn(receiverId, messageDtoFromDb.getChatRoomId())) {
                fcmNotificationService.sendChatNotification(receiverId, messageDtoFromDb);
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

        // ✅ 서버에서 멘션 리스트까지 내려주고 싶으면, 메시지 엔티티에 저장 구조를 추가해야 함
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
