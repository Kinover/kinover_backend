package com.example.kinover_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@Entity
public class UserChatRoom {
    @Id
    @GeneratedValue
    private UUID userChatRoomId;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "userId", nullable = false)
    private User user;


    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "chatRoomId", nullable = false)
    private ChatRoom chatRoom;

    @Column(columnDefinition = "DATE")
    private Date joinedAt; // 유저가 채팅방에 입장한 시간 등 추가 정보 가능
}
