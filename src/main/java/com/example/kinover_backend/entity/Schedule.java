package com.example.kinover_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@Entity
public class Schedule {

    @Id
    @GeneratedValue
    private UUID scheduleId;

    @Column(columnDefinition = "VARCHAR(255)")
    private String title;

    @Column(columnDefinition = "VARCHAR(1000)")
    private String memo;

    @Column(columnDefinition = "boolean")
    private boolean isPersonal;

    @Column(columnDefinition = "DATE")
    private LocalDate date;

    @ManyToOne
    @JoinColumn(name = "userId", nullable = true)
    private User user;

    @ManyToOne
    @JoinColumn(name = "familyId", nullable = false)
    private Family family;

    /**
     * 낙관적 락을 위한 버전 필드
     */
    @Version
    private Long version;
}