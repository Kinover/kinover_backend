// src/main/java/com/example/kinover_backend/dto/UpdatePostRequest.java
package com.example.kinover_backend.dto;

import com.example.kinover_backend.enums.PostType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class UpdatePostRequest {
    private Long authorId;              // ✅ 프론트에서 보내고, 토큰 유저와 비교
    private UUID familyId;          // ✅ 필요하면 추가
    private String content;             // (선택) 수정할 내용
    private UUID categoryId;            // (선택) 수정할 카테고리

    // ✅ 이미지/영상 최종 리스트(선택)
    // - 수정 요청이 있을 때만 내려주면 됨 (null이면 "이미지 수정 안함")
    private List<String> imageUrls;

    // ✅ 서버가 영상/이미지 구분하려면 필수
    // - imageUrls와 길이 동일해야 함
    private List<PostType> postTypes;
}
