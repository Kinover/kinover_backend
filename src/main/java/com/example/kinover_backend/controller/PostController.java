package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.PostDTO;
import com.example.kinover_backend.service.PostService;
import com.example.kinover_backend.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
