package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.UserFamily;
import lombok.Getter;

import java.util.UUID;

@Getter
public class UserFamilyDTO {
    private UUID userFamilyId;
    private String role;
    private Long userId;   // User ID
    private UUID familyId; // Family ID

    // UserFamily 엔티티를 UserFamilyDTO로 변환하는 생성자
    public UserFamilyDTO(UserFamily userFamily) {
        if(userFamily.getUserFamilyId()==null){
            userFamily.setUserFamilyId(UUID.randomUUID());
        }
        this.userFamilyId = userFamily.getUserFamilyId();
        this.role = userFamily.getRole();
        this.userId = userFamily.getUser().getUserId();    // User 엔티티에서 userId를 가져옴
        this.familyId = userFamily.getFamily().getFamilyId(); // Family 엔티티에서 familyId를 가져옴
    }
}
