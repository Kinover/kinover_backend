package com.example.kinover_backend.entity;

import com.example.kinover_backend.enums.ChallengeCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;


@Getter
@Setter
@Entity
public class RecChallenge {
    @Id
    @GeneratedValue
    private UUID recChallengeId;

    @Column(columnDefinition = "VARCHAR(255)")
    private String title;

    @ElementCollection
    @CollectionTable(
            name = "rec_challenge_category", // 생성될 테이블 이름
            joinColumns = @JoinColumn(name = "recChallengeId") // 외래 키 연결
    )
    @Column(columnDefinition = "VARCHAR(255)")
    private List<String> categories;

    @Column(columnDefinition = "VARCHAR(100)")
    private String duration;

    // 추가 필드: 일주일에 며칠 할 것인지 (예: 3일)
    @Column(columnDefinition = "INT")
    private int daysPerWeek;

    @Column(columnDefinition="INT")
    private int dailyTimeInMinutes;

    @Column(columnDefinition = "VARCHAR(100)")
    private String rewardDescription;

    @Column(columnDefinition = "VARCHAR(255)")
    private String taskDescription;

    @Column(name = "image", columnDefinition = "VARCHAR(255)")
    private String image;

}
