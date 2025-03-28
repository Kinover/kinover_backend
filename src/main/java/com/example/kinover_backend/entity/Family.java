package com.example.kinover_backend.entity;

import com.example.kinover_backend.enums.FamilyRelationship;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Date;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
public class Family {
    // 메서드
    @Id
    @GeneratedValue
    private UUID familyId;

    @Column(columnDefinition = "VARCHAR(30)")
    private String name;

    @Column(columnDefinition = "DATE")
    private Date createdAt;

    @Column(columnDefinition = "DATE")
    private Date updatedAt;

    @Enumerated(EnumType.STRING) // Enum 값을 문자열로 저장
    private FamilyRelationship relationship;

    @Column(columnDefinition = "VARCHAR(255)")
    private String notice;

    @OneToMany(mappedBy = "family", cascade = CascadeType.ALL)
    private List<UserFamily> userFamilies;

}
