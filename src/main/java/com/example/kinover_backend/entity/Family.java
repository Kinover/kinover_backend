package com.example.kinover_backend.entity;

import com.example.kinover_backend.enums.FamilyRelationship;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Date;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
public class Family {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // DB(JPA)가 UUID 자동 생성
    @Column(columnDefinition = "BINARY(16)") // UUID 저장을 위한 최적화 (선택사항, 생략 가능)
    private UUID familyId;

    @Column(columnDefinition = "VARCHAR(30)")
    private String name;

    // DB 기본값도 있지만, JPA 저장 시 null로 들어가는 문제를 방지하기 위해 어노테이션 추가
    @CreationTimestamp 
    @Column(updatable = false, columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private Date createdAt;

    @UpdateTimestamp
    @Column(columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private Date updatedAt;

    @Enumerated(EnumType.STRING)
    private FamilyRelationship relationship;

    @Column(columnDefinition = "VARCHAR(255)")
    private String notice;

    @OneToMany(mappedBy = "family", cascade = CascadeType.ALL)
    private List<UserFamily> userFamilies;
}