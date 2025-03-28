package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.enums.FamilyRelationship;
import lombok.Getter;
import org.webjars.NotFoundException;

import java.sql.Date;
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
            throw new NotFoundException("Family not found for id: " + familyId);
        }
        if(family.getFamilyId()==null){
            family.setFamilyId(UUID.randomUUID());
        }
        this.familyId = family.getFamilyId();
        this.name = family.getName();
        this.createdAt = family.getCreatedAt();
        this.updatedAt = family.getUpdatedAt();
        this.notice = family.getNotice();
        this.relationship =family.getRelationship();
    }
}
