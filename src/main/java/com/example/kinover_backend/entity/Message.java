package com.example.kinover_backend.entity;

import com.example.kinover_backend.enums.MessageType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "message")
public class Message {

    @Id
    @Column(name = "message_id", columnDefinition = "BINARY(16)")
    private UUID messageId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    // MariaDB에선 created_at이 default current_timestamp()라 insertable=false로 지정
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private MessageType messageType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

}
