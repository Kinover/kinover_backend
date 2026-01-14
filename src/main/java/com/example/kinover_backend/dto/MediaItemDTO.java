package com.example.kinover_backend.dto;

import com.example.kinover_backend.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaItemDTO {
    private UUID messageId;
    private UUID chatRoomId;

    private Long senderId;
    private String senderName;
    private String senderImage;

    private MessageType messageType; // image | video

    private String url;              // 개별 미디어 URL
    private int orderInMessage;      // 같은 메시지 내 index

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime createdAt;
}
