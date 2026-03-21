package com.example.kinover_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CommentDTO {

    public CommentDTO() {
    }

    public CommentDTO(
            UUID commentId,
            UUID postId,
            String content,
            Long authorId,
            String authorName,
            String authorImage,
            Date createdAt) {
        this.commentId = commentId;
        this.postId = postId;
        this.content = content;
        this.authorId = authorId;
        this.authorName = authorName;
        this.authorImage = authorImage;
        this.createdAt = createdAt;
    }

    private UUID commentId;       // 서버 응답 시 채워짐
    private String content;       // 요청/응답 공통
    private UUID postId;// 요청 시 사용
    private Long authorId;        // 요청 시 사용

    // ✅ 멘션 대상 userId 목록 (프론트에서 @선택한 유저들)
    private List<Long> mentionUserIds;

    private String authorName;    // 응답 시 추가
    private String authorImage;   // 응답 시 추가
    private Date createdAt;       // 응답 시 자동 포함
}
