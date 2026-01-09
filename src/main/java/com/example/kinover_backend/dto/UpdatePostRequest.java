package com.example.kinover_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class UpdatePostRequest {
    private Long authorId;        // ✅ 프론트에서 보내고, 토큰 유저와 비교
    private String content;       // 수정할 내용(선택)
    private UUID categoryId;      // 수정할 카테고리(선택)
    private List<String> imageUrls; // 수정 후 최종 이미지 리스트(선택)
}
