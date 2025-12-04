package com.example.kinover_backend.entity;

import com.example.kinover_backend.enums.ChatBotPersonality;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Date;

@Getter
@Setter
@Entity
public class ChatRoom {
    @Id
    @GeneratedValue
    private UUID chatRoomId;

    @Column(columnDefinition = "VARCHAR(255)")
    private String roomName;

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean isKino; // RoomType 대신 kino 여부만 체크

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private ChatBotPersonality personality;

    @Column(columnDefinition = "VARCHAR(55)")
    private String familyType;

    @Column(columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private Date createdAt;

    @Column(columnDefinition = "VARCHAR(255)")
    private String image;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "familyId", nullable = true)
    private Family family;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL)
    private List<UserChatRoom> userChatRooms;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL)
    private List<Message> messages = new ArrayList<>();

}