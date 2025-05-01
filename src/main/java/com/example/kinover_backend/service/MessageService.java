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
        Message message = new Message();
        message.setContent(dto.getContent());
        message.setMessageType(dto.getMessageType() != null ? dto.getMessageType() : MessageType.text);
        message.setCreatedAt(dto.getCreatedAt());

        userRepository.findById(dto.getSender().getUserId())
                .ifPresent(message::setSender);

        chatRoomRepository.findById(dto.getChatRoom().getChatRoomId())
                .ifPresent(message::setChatRoom);

        Message saved = messageRepository.save(message);

        try {
            String json = objectMapper.writeValueAsString(new MessageDTO(saved));
            redisTemplate.convertAndSend(channelTopic.getTopic(), json);
        } catch (Exception e) {
            throw new RuntimeException("Redis 발행 중 오류", e);
        }
    }

    public List<MessageDTO> getAllMessagesByChatRoomId(UUID chatRoomId) {
        return messageRepository.findAllByChatRoomId(chatRoomId).stream()
                .map(MessageDTO::new)
                .collect(Collectors.toList());
    }
}
