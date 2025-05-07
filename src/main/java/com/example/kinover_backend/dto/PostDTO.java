package com.example.kinover_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class PostDTO {

    // 클라이언트 → 서버 요청 시 포함 (작성 시)
    private Long authorId;
    private Long categoryId;
    private List<String> imageUrls;
    private String content;

    // 서버 → 클라이언트 응답 시 채워짐
    private UUID postId;
    private Long familyId;
    private String authorName;
    private String authorImage;
    private Date createdAt;
    private int commentCount;
}
