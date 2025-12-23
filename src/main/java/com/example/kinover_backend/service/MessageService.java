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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

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

    // ✅ 추가 주입
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

        // ✅ Redis 발행 (기존)
        try {
            MessageDTO responseDto = getMessageDTO(saved);
            String json = objectMapper.writeValueAsString(responseDto);
            redisTemplate.convertAndSend(channelTopic.getTopic(), json);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Redis 발행 중 오류", e);
        }

        // ✅ 여기서 FCM 알림 발송 (멘션 분기)
        sendChatPushNotifications(dto);
    }

    private void sendChatPushNotifications(MessageDTO dto) {
        // 채팅방 참여자 리스트 (UserDTO)
        List<UserDTO> users = chatRoomService.getUsersByChatRoom(dto.getChatRoomId());

        // ✅ 멘션 대상 set (중복 제거 + null 제거 + 본인 제거)
        Set<Long> mentionTargets = Optional.ofNullable(dto.getMentionUserIds())
                .orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.equals(dto.getSenderId()))
                .collect(Collectors.toSet());

        // ✅ 1) 멘션 대상자: 설정 무시하고 무조건 멘션 알림
        for (UserDTO u : users) {
            Long userId = u.getUserId();
            if (mentionTargets.contains(userId)) {
                fcmNotificationService.sendMentionChatNotification(userId, dto);
            }
        }

        // ✅ 2) 나머지: 기존 로직(전체/채팅방 설정 true일 때만)
        for (UserDTO u : users) {
            Long userId = u.getUserId();

            if (userId.equals(dto.getSenderId())) continue;      // 보낸 사람 제외
            if (mentionTargets.contains(userId)) continue;       // 멘션은 중복 발송 방지

            // 기존 체크 사용
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

        // ✅ 서버에서 내려줄 때 멘션 리스트는 선택 (원하면 저장/전송 구조 더 늘려야 함)
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
