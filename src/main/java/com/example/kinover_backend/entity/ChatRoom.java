package com.example.kinover_backend.entity;

import com.example.kinover_backend.enums.ChatBotPersonality;
import com.example.kinover_backend.enums.KinoType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
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
    private boolean isKino;

    // ✅ 이 필드가 빠져있었음
    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private KinoType kinoType;

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

    // ✅ 저장 직전에 키노방이면 kinoType 기본값 강제
    @PrePersist
    public void prePersist() {
        if (isKino && kinoType == null) {
            kinoType = KinoType.YELLOW_KINO;
        }
    }
}
