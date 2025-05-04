package com.example.kinover_backend.entity;

import com.example.kinover_backend.enums.MessageType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@Entity
public class Message {

    @Id
    @Column(name = "message_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID messageId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType messageType;

    @ManyToOne
    @JoinColumn(name = "chatRoomId", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senderId", nullable = false)
    private User sender;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
