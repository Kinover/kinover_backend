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
public class Message {
    @Id
    @GeneratedValue
    private UUID messageId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "DATETIME")
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "chatRoomId", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)//여러 메세지가 한명의 sender와 연결됨.
    @JoinColumn(name = "senderId", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING) // ENUM 값을 문자열로 저장
    @Column(columnDefinition = "VARCHAR(50)", nullable = false)
    private MessageType messageType; // Enum 타입 text, image, video..

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}