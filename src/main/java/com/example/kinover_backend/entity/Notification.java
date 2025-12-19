package com.example.kinover_backend.entity;

import com.example.kinover_backend.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue
    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "comment_id")
    private UUID commentId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP"
    )
    private LocalDateTime createdAt;
}
