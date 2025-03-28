package com.example.kinover_backend.entity;

import com.example.kinover_backend.enums.RoomType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
public class ChatRoom {
    @Id
    @GeneratedValue
    private UUID chatRoomId;

    @Column(columnDefinition = "VARCHAR(255)")
    private String roomName;

    @Enumerated(EnumType.STRING)  // Enum 값을 문자열로 저장
    @Column(columnDefinition = "VARCHAR(55)")
    private RoomType roomType;
    // 채팅방 유형 ("group", "private")

    @Column(columnDefinition = "VARCHAR(55)")
    private String familyType;
    // 가족 여부 ("family", "personal")

    @Column(columnDefinition = "DATE")
    private Date createdAt;

    @Column(columnDefinition = "VARCHAR(255)")
    private String image;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "familyId", nullable = true)
    private Family family;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL)
    private List<UserChatRoom> userChatRooms; // 여러 유저가 참여하는 채팅방

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL)
    private List<Message> messages = new ArrayList<>(); // 여러 메세지를 가지고 있음


}
