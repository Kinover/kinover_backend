package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.CommentDTO;
import com.example.kinover_backend.service.CommentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.kinover_backend.JwtUtil;

import java.util.List;
import java.util.UUID;

@Tag(name = "댓글 Controller", description = "댓글 조회, 생성, 삭제 API를 제공합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<Void> createComment(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CommentDTO dto) {

        String token = authHeader.replace("Bearer ", "");
        Long authenticatedUserId = jwtUtil.getUserIdFromToken(token);

        if (!authenticatedUserId.equals(dto.getAuthorId())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        commentService.createComment(dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{postId}")
    public ResponseEntity<List<CommentDTO>> getComments(@PathVariable UUID postId) {
        return ResponseEntity.ok(commentService.getCommentsForPost(postId));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable UUID commentId) {
        commentService.deleteComment(commentId);
        return ResponseEntity.ok().build();
    }
}
