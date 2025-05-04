package com.example.kinover_backend.dto;

import com.example.kinover_backend.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private UUID messageId;
    private String content;
    private UUID chatRoomId;
    private Long senderId;
    private MessageType messageType;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime createdAt;

    public MessageDTO(UUID messageId, String content, UUID chatRoomId, Long senderId,
                      MessageType messageType, LocalDateTime createdAt) {
        this.messageId = messageId;
        this.content = content;
        this.chatRoomId = chatRoomId;
        this.senderId = senderId;
        this.messageType = messageType;
        this.createdAt = createdAt;
    }
}
