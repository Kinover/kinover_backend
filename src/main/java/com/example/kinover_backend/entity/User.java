package com.example.kinover_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.example.kinover_backend.enums.UserEmotion;

@Getter
@Setter
@Entity
@Table(name = "user")
public class User {
    @Id
    private Long userId;

    @Version  // 낙관적 락 적용, 초기값은 DB에서 DEFAULT 0으로 설정
    private Integer version;

    @Column(columnDefinition = "VARCHAR(100)")
    private String name;

    @Column(columnDefinition = "DATE")
    private Date birth;

    @Column(columnDefinition = "VARCHAR(255)")
    private String email;

    @Column(columnDefinition = "VARCHAR(255)")
    private String pwd;

    // 기존 String emotion 제거하고 아래로 교체
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private UserEmotion emotion;  // 오늘의 감정 상태

    @Column(nullable = false)
    private Boolean isOnline = false;  // 현재 접속 상태 (true/false)

    @Column(columnDefinition = "TIMESTAMP")
    private LocalDateTime lastActiveAt;  // 마지막 활동 시간

    @Column(columnDefinition = "TIMESTAMP")
    private LocalDateTime lastNotificationCheckedAt;

    @Column(columnDefinition = "TEXT")
    private String trait;

    @Column(columnDefinition = "TEXT")
    private String image;

    @Column(columnDefinition = "VARCHAR(20)")
    private String phoneNumber;

    @Column(columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private Date createdAt;

    @Column(columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private Date updatedAt;

    @OneToMany(mappedBy = "sender", fetch = FetchType.LAZY)
    private List<Message> messages = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<UserFamily> userFamilyList;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<UserChatRoom> userChatRooms;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Schedule> scheduleList;

    @Column(nullable = false)
    private Boolean isPostNotificationOn = true;

    @Column(nullable = false)
    private Boolean isCommentNotificationOn = true;

    @Column(nullable = false)
    private Boolean isChatNotificationOn = true;

    @Column(columnDefinition = "TIMESTAMP")
    private LocalDateTime emotionUpdatedAt;  // 감정을 마지막으로 수정한 시간
}