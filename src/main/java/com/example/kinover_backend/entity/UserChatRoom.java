// src/main/java/com/example/kinover_backend/entity/UserChatRoom.java
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
@Table(name = "user_chat_room")
public class UserChatRoom {

    @Id
    @GeneratedValue
    private UUID userChatRoomId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatRoomId", nullable = false)
    private ChatRoom chatRoom;

    @Column(columnDefinition = "DATE")
    private Date joinedAt;

    // ✅ 읽음 포인터(핵심)
    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    // ✅ 추가: 유저별 채팅방 표시 이름
    @Column(name = "display_room_name", columnDefinition = "VARCHAR(255)")
    private String displayRoomName;

    // ✅ 추가: 유저가 직접 채팅방 이름을 바꿨는지(자동 갱신 정책에 필요)
    @Column(name = "is_custom_room_name", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean customRoomName;

    // ⚠️ DB에 추가해둔 last_read_message_id(BIGINT)는 UUID랑 타입이 안 맞아서
    // 지금은 사용하지 않는 걸 권장. (칼럼은 남아도 JPA는 무시함)
    // @Column(name = "last_read_message_id")
    // private Long lastReadMessageId;
}
