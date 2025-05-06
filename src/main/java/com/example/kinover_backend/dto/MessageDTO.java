package com.example.kinover_backend.dto;

import com.example.kinover_backend.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MessageDTO {
    private UUID messageId;
    private String content;
    private UUID chatRoomId;
    private Long senderId;
    private String senderName;
    private String senderImage;
    private MessageType messageType;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime createdAt;
}
