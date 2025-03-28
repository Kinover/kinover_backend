package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.Message;
import com.example.kinover_backend.entity.User;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Getter
public class MessageDTO {
    private UUID messageId;
    private String content;
    private LocalDateTime createdAt;
    private ChatRoomDTO chatRoom;  // ChatRoom의 ID만 저장
    private String messageType;
    private UserDTO sender;

    // Message 엔티티를 MessageDTO로 변환하는 생성자
    public MessageDTO(Message message) {
        if(message.getMessageId()==null){
            message.setMessageId(UUID.randomUUID());
        }
        this.messageId = message.getMessageId();
        this.content = message.getContent();
        this.createdAt = message.getCreatedAt();
        this.chatRoom = new ChatRoomDTO(message.getChatRoom()); // ChatRoomDTO로 변환
        this.messageType = message.getMessageType();
        this.sender = new UserDTO(message.getSender()); }
}
