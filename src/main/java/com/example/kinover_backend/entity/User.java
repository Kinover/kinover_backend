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

    @Column(name = "kakao_id", unique = true)
    private Long kakaoId;

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
    private List<UserFamily> userFamilyList = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<UserChatRoom> userChatRooms = new ArrayList<>();

    /**
     * ✅ 참여 중인 일정 목록
     * - Schedule.participants (ManyToMany) 와 양방향 매핑
     * - 기존 mappedBy="user"는 Schedule에 user 필드가 없어서 서버가 죽었음
     */
    @ManyToMany(mappedBy = "participants", fetch = FetchType.LAZY)
    private List<Schedule> scheduleList = new ArrayList<>();

    @Column(nullable = false)
    private Boolean isPostNotificationOn = true;

    @Column(nullable = false)
    private Boolean isCommentNotificationOn = true;

    @Column(nullable = false)
    private Boolean isChatNotificationOn = true;

    @Column(columnDefinition = "TIMESTAMP")
    private LocalDateTime emotionUpdatedAt;  // 감정을 마지막으로 수정한 시간

    @Column(name = "terms_agreed")
    private Boolean termsAgreed;

    @Column(name = "privacy_agreed")
    private Boolean privacyAgreed;

    @Column(name = "marketing_agreed")
    private Boolean marketingAgreed;

    @Column(name = "terms_version")
    private String termsVersion;

    @Column(name = "privacy_version")
    private String privacyVersion;

    @Column(name = "agreed_at")
    private Date agreedAt;

    @Column(name = "marketing_agreed_at")
    private Date marketingAgreedAt;
}
