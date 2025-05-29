package com.example.kinover_backend.dto;

import com.example.kinover_backend.enums.NotificationType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDTO {
    private NotificationType notificationType;
    private UUID postId;       // nullable
    private UUID commentId;    // nullable
    private LocalDateTime createdAt;
    private String authorName;
    private String authorImage;
    private String categoryTitle;
    private String contentPreview;
    private String firstImageUrl;
}
