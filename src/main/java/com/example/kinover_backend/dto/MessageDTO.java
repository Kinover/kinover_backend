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
    private LocalDateTime createdAt;
    private ChatRoomDTO chatRoom;
    private MessageType messageType;
    private UserDTO sender;

    public MessageDTO() {}

    public MessageDTO(Message message) {
        if (message.getMessageId() == null) {
            message.setMessageId(UUID.randomUUID());
        }
        this.messageId = message.getMessageId();
        this.content = message.getContent();
        this.createdAt = message.getCreatedAt();
        this.chatRoom = new ChatRoomDTO(message.getChatRoom());
        this.messageType = message.getMessageType();
        this.sender = new UserDTO(message.getSender());
    }
}