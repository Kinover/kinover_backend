package com.example.kinover_backend.entity;

import com.example.kinover_backend.enums.ChallengeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@Entity
public class Challenge {
    @Id
    @GeneratedValue
    private UUID challengeId;

    @Column
    private ChallengeStatus status;

    @Column(columnDefinition = "VARCHAR(255)")
    private String title;

    @Column(columnDefinition = "DATE")
    private Date startAt;

    @Column(columnDefinition ="DATE")
    private Date endAt;

    @Column(columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private java.util.Date createdAt;

    @ManyToOne
    @JoinColumn(name = "familyId")
    private Family family;

    public Challenge() {
    }

    public Challenge(Family family, RecChallenge recChallenge){
        this.title = recChallenge.getTitle();
        this.family=family;
    }

}
