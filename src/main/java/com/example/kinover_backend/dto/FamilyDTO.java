package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.enums.FamilyRelationship;
import jakarta.persistence.EntityNotFoundException;
import lombok.Getter;

import java.util.Date;
import java.util.UUID;

@Getter
public class FamilyDTO {
    private UUID familyId;
    private String name;
    private Date createdAt;
    private Date updatedAt;
    private String notice;
    private FamilyRelationship relationship;

    public FamilyDTO(Family family) {
        if (family == null) {
            throw new EntityNotFoundException("Family not found");
        }
        if (family.getFamilyId() == null) {
            family.setFamilyId(UUID.randomUUID());
        }
        this.familyId = family.getFamilyId();
        this.name = family.getName();
        this.createdAt = family.getCreatedAt();
        this.updatedAt = family.getUpdatedAt();
        this.notice = family.getNotice();
        this.relationship = family.getRelationship();
    }
}