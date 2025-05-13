package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.MessageDTO;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTopic channelTopic = new ChannelTopic("chat:messages");

    @Value("${cloudfront.domain}")
    private String cloudFrontDomain;

    public void addMessage(MessageDTO dto) {
        // ID로 ChatRoom과 User 조회
        var chatRoom = chatRoomRepository.findById(dto.getChatRoomId())
                .orElseThrow(() -> new IllegalArgumentException("ChatRoom not found"));
        System.out.println("[DEBUG] 조회된 ChatRoom: " + chatRoom.getChatRoomId());

        var sender = userRepository.findById(dto.getSenderId())
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));
        System.out.println("[DEBUG] 조회된 Sender: " + sender.getUserId() + ", name=" + sender.getName());

        // 메시지 생성
        Message message = new Message();
        message.setMessageId(UUID.randomUUID());
        message.setContent(dto.getContent());
        message.setChatRoom(chatRoom);
        message.setSender(sender);
        message.setMessageType(dto.getMessageType());

        if (dto.getMessageType() == MessageType.image || dto.getMessageType() == MessageType.video) {
            List<String> fileNames = dto.getImageUrls();
            if (fileNames == null || fileNames.isEmpty()) {
                throw new RuntimeException("이미지 파일명이 비어 있습니다.");
            }

            List<String> imageUrls = fileNames.stream()
                    .map(fileName -> cloudFrontDomain + fileName)
                    .collect(Collectors.toList());

            String content = String.join(",", imageUrls); // ,로 이어붙이기
            message.setContent(content);
        } else {
            message.setContent(dto.getContent());
        }

        Message saved = messageRepository.save(message);

        // Redis 발행
        try {
            MessageDTO responseDto = getMessageDTO(saved);

            System.out.println("[Redis 발행용 DTO] " + responseDto);

            String json = objectMapper.writeValueAsString(responseDto);
            System.out.println("[Redis 직렬화된 JSON] " + json);

            redisTemplate.convertAndSend(channelTopic.getTopic(), json);
        } catch (Exception e) {
            e.printStackTrace(); // 반드시 로그 출력
            throw new RuntimeException("Redis 발행 중 오류", e);
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
            // CloudFront URL들이 콤마(,)로 저장되어 있다고 가정하고 분리
            if (saved.getContent() != null && !saved.getContent().isEmpty()) {
                List<String> imageUrls = List.of(saved.getContent().split(","));
                responseDto.setImageUrls(imageUrls);
            } else {
                responseDto.setImageUrls(List.of());
            }
            responseDto.setContent(null);
        } else {
            responseDto.setContent(saved.getContent());
            responseDto.setImageUrls(null);
        }

        return responseDto;
    }

    public List<MessageDTO> fetchMessagesBefore(UUID chatRoomId, LocalDateTime before, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        LocalDateTime beforeTime = (before != null) ? before : LocalDateTime.now();

        List<Message> messages = new ArrayList<>(
                messageRepository.findByChatRoom_ChatRoomIdAndCreatedAtBefore(chatRoomId, beforeTime, pageRequest)
                        .getContent()
        );

        Collections.reverse(messages); // 오래된 순으로 보여주기 위해

        return messages.stream()
                .map(MessageService::getMessageDTO)
                .toList();
    }
}