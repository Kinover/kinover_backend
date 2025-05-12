package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.PostDTO;
import com.example.kinover_backend.service.PostService;
import com.example.kinover_backend.JwtUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.Map;

@Tag(name = "게시글 Controller", description = "게시글 조회, 삭제 API를 제공합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<Void> createPost(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody PostDTO postDTO
    ) {
        String token = authorizationHeader.replace("Bearer ", "");
        Long authenticatedUserId = jwtUtil.getUserIdFromToken(token);

        if (!authenticatedUserId.equals(postDTO.getAuthorId())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        postService.createPost(postDTO);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{postId}/image")
    public ResponseEntity<Void> deleteImageFromPost(
            @PathVariable UUID postId,
            @RequestBody Map<String, String> request) {
        String imageUrl = request.get("imageUrl");
        postService.deleteImage(postId, imageUrl);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID postId) {
        postService.deletePost(postId);
        return ResponseEntity.ok().build();
    }
}
