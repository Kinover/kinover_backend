package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.Category;
import com.example.kinover_backend.entity.Family;
import jakarta.persistence.EntityNotFoundException;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Getter
@Setter
public class CategoryDTO {

    private UUID categoryId;      // 서버 응답 시 채워짐
    private String title;         // 요청/응답 공통
    private UUID familyId;        // 요청 시 사용
    private Date createdAt;       // 응답 시 자동 포함
}