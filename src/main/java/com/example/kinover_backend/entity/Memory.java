package com.example.kinover_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Setter
@Getter
@Entity
public class Memory {
    @Id
    @GeneratedValue
    private UUID memoryId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "DATE")
    private Date date;

    @Column(columnDefinition = "VARCHAR(100)")
    private String image;

    @Column(columnDefinition = "DATE")
    private Date createdAt;

    // 외래키
    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "familyId", nullable = false)
    private Family family;  // 추억이 속한 가족

    @ManyToOne(cascade=CascadeType.ALL)
    @JoinColumn(name="userId", nullable=false)
    private User user;
}
