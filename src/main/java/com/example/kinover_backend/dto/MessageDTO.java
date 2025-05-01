package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.Message;
import com.example.kinover_backend.enums.MessageType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class MessageDTO {
    private UUID messageId;
    private String content;
    private ChatRoomDTO chatRoom;
    private MessageType messageType;
    private UserDTO sender;

    private LocalDateTime createdAt;

    public MessageDTO() {}

    public MessageDTO(Message message) {
        if (message.getMessageId() == null) {
            message.setMessageId(UUID.randomUUID());
        }
        this.messageId = message.getMessageId();
        this.content = message.getContent();
        this.chatRoom = new ChatRoomDTO(message.getChatRoom());
        this.messageType = message.getMessageType();
        this.sender = new UserDTO(message.getSender());
        this.createdAt = message.getCreatedAt(); 
    }
}
