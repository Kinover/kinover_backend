package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.entity.Message;
import com.example.kinover_backend.enums.MessageType;
import com.example.kinover_backend.repository.ChatRoomRepository;
import com.example.kinover_backend.repository.MessageRepository;
import com.example.kinover_backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

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

    public void addMessage(MessageDTO dto) {
        // ID로 ChatRoom과 User 조회
        var chatRoom = chatRoomRepository.findById(dto.getChatRoomId())
                .orElseThrow(() -> new IllegalArgumentException("ChatRoom not found"));

        var sender = userRepository.findById(dto.getSenderId())
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        // 메시지 생성
        Message message = new Message();
        message.setMessageId(UUID.randomUUID());
        message.setContent(dto.getContent());
        message.setMessageType(dto.getMessageType() != null ? dto.getMessageType() : MessageType.text);
        message.setChatRoom(chatRoom);
        message.setSender(sender);

        Message saved = messageRepository.save(message);

        // Redis 발행
        try {
            MessageDTO responseDto = new MessageDTO(
                    saved.getMessageId(),
                    saved.getContent(),
                    saved.getChatRoom().getChatRoomId(),
                    saved.getSender().getUserId(),
                    saved.getSender().getName(),
                    saved.getSender().getImage(),
                    saved.getMessageType(),
                    saved.getCreatedAt()
            );

            System.out.println("[Redis 발행용 DTO] " + responseDto);

            String json = objectMapper.writeValueAsString(responseDto);
            System.out.println("[Redis 직렬화된 JSON] " + json);

            redisTemplate.convertAndSend(channelTopic.getTopic(), json);
        } catch (Exception e) {
            e.printStackTrace(); // 반드시 필요
            throw new RuntimeException("Redis 발행 중 오류", e);
        }

    public List<MessageDTO> getAllMessagesByChatRoomId(UUID chatRoomId) {
        return messageRepository.findAllByChatRoomId(chatRoomId).stream()
                .map(message -> new MessageDTO(
                        message.getMessageId(),
                        message.getContent(),
                        message.getChatRoom().getChatRoomId(),
                        message.getSender().getUserId(),
                        message.getSender().getName(),
                        message.getSender().getImage(),
                        message.getMessageType(),
                        message.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }
}