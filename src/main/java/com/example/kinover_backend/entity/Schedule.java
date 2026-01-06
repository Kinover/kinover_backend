// src/main/java/com/example/kinover_backend/entity/Schedule.java
package com.example.kinover_backend.entity;

import com.example.kinover_backend.enums.ScheduleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
public class Schedule {

    @Id
    @GeneratedValue
    private UUID scheduleId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 1000)
    private String memo;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleType type;

    // ✅ 추가: 개인 여부 (DB not null 대응: primitive boolean 사용)
    @Column(nullable = false)
    private boolean personal = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "schedule_participants",
        joinColumns = @JoinColumn(name = "schedule_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> participants = new HashSet<>();

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", referencedColumnName = "userId")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;
}
