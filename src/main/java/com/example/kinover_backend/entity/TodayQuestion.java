package com.example.kinover_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
public class TodayQuestion {
    @Id
    @GeneratedValue
    private UUID todayQuestionId;

    @Column(columnDefinition = "VARCHAR(255)")
    private String question;

}
