package com.example.kinover_backend.dto;

import com.example.kinover_backend.enums.NotificationType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponseDTO {
    private LocalDateTime lastCheckedAt;
    private List<NotificationDTO> notifications;
}

