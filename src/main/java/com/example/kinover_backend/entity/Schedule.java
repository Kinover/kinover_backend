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

    // ✅ 필수 type
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleType type;

    /**
     * ✅ 참여 구성원(다중 선택)
     * - INDIVIDUAL / FAMILY: 1명 이상 가능
     * - ANNIVERSARY: 비워야 함
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "schedule_participants",
        joinColumns = @JoinColumn(name = "schedule_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> participants = new HashSet<>();

    /**
     * (선택) 작성자/생성자 기록이 필요하면 유지
     * 지금 요구사항엔 필수는 아니라서 nullable로 둠.
     */
    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", referencedColumnName = "userId")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;
}
