package com.example.kinover_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Getter
@Setter
public class CommentDTO {

    private UUID commentId;       // 서버 응답 시 채워짐
    private String content;       // 요청/응답 공통
    private UUID postId;// 요청 시 사용
    private Long authorId;        // 요청 시 사용

    private String authorName;    // 응답 시 추가
    private String authorImage;   // 응답 시 추가
    private Date createdAt;       // 응답 시 자동 포함
}