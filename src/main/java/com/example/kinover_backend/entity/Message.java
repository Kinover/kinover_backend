package com.example.kinover_backend.entity;

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
    @GeneratedValue
    private UUID messageId;

//    @Column
//    private Long sender;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TIMESTAMP")
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "chatRoomId", nullable = false)  // ChatRoom과 연결
    private ChatRoom chatRoom;  // chatRoom 필드 추가// chatRoomId만 저장

    @ManyToOne(fetch = FetchType.LAZY)  // 여러 메시지가 하나의 sender와 연결됨
    @JoinColumn(name = "senderId", nullable = false)
    private User sender;  // 발신자 (유저)

    @Column(columnDefinition = "VARCHAR(50)")
    private String messageType; // 메세지 타입(텍스트, 사진, 영상 ...

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();  // 저장 전 현재 시간 자동 설정
    }
}
