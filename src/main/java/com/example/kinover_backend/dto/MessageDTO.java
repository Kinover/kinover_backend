package com.example.kinover_backend.dto;

import com.example.kinover_backend.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
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

    // ✅ 통일: imageUrls
    private List<String> imageUrls;

    // ✅ 멘션: 프론트가 선택 기반으로 넣어서 보냄
    private List<Long> mentionUserIds;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime createdAt;
}
