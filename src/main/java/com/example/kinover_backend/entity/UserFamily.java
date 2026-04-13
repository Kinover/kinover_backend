package com.example.kinover_backend.entity;

import com.example.kinover_backend.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
public class UserFamily {
    @Id
    @GeneratedValue
    private UUID userFamilyId;

    @Column
    private String role;

    @ManyToOne
    @JoinColumn(name = "user_id",  nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    /** 가족 참여 시각. 이 시각 이전에 쌓인 가족 알림은 해당 멤버에게 내려주지 않는다(null = 마이그레이션 전 레코드, 기존 동작 유지). */
    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

}
