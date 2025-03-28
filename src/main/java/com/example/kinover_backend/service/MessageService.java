package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.entity.Message;
import com.example.kinover_backend.repository.ChatRoomRepository;
import com.example.kinover_backend.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final StringRedisTemplate redisTemplate;
    private final ChannelTopic channelTopic;  // Redis 채널 이름

    @Autowired
    public MessageService(MessageRepository messageRepository, ChatRoomRepository chatRoomRepository, StringRedisTemplate redisTemplate) {
        this.messageRepository = messageRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.redisTemplate = redisTemplate;
        this.channelTopic = new ChannelTopic("chat:messages");
    }

    // 채팅방 아이디를 통해 모든 메시지 조회 (MessageDTO 반환)
    public List<MessageDTO> getAllMessagesByChatRoomId(UUID chatRoomId) {
        List<Message> messages = messageRepository.findAllByChatRoomId(chatRoomId);
        return messages.stream()
                .map(MessageDTO::new)  // Message 엔티티를 MessageDTO로 변환
                .collect(Collectors.toList());
    }

    // 메세지 추가 및 Redis 발행 (MessageDTO 반환)
    public MessageDTO addMessage(Message message) {
        // 메시지 저장
        Message savedMessage = messageRepository.save(message);

        // Redis에 메시지 발행
        redisTemplate.convertAndSend(channelTopic.getTopic(), savedMessage.getContent());  // 메시지 내용 발행

        return new MessageDTO(savedMessage);  // 저장된 메시지를 DTO로 반환
    }

    // 메세지 저장
    public Message saveMessage(Message message) {
        try {
            return messageRepository.save(message);  // 메시지 저장
        } catch (Exception e) {
            throw new RuntimeException("메시지 저장 중 오류 발생", e);
        }
    }
}
