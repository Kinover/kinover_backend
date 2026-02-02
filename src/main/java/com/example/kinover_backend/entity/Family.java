package com.example.kinover_backend.entity;

import com.example.kinover_backend.enums.FamilyRelationship;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Date;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
public class Family {

  @Id
    @UuidGenerator
    @Column(name = "family_id", columnDefinition = "BINARY(16)", nullable = false)
    @JdbcTypeCode(SqlTypes.BINARY) // ✅ UUID를 BINARY로 저장하도록 고정
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