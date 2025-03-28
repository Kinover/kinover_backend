package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.Comment;
import com.example.kinover_backend.entity.Memory;
import lombok.Getter;
import java.util.Date;
import java.util.UUID;

@Getter
public class CommentDTO {
    private UUID commentId;
    private String content;
    private Date createdAt;
    private MemoryDTO memory;  // Memory 엔티티의 DTO (순환참조 방지)

    // Comment 엔티티를 CommentDTO로 변환하는 생성자
    public CommentDTO(Comment comment) {
        if(comment.getCommentId()==null){
            comment.setCommentId(UUID.randomUUID());
        }
        this.commentId = comment.getCommentId();
        this.content = comment.getContent();
        this.createdAt = comment.getCreatedAt();
        this.memory = new MemoryDTO(comment.getMemory()); // MemoryDTO로 변환
    }
}
