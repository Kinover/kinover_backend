package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.PostDTO;
import com.example.kinover_backend.service.PostService;
import com.example.kinover_backend.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.Map;

@Tag(name = "게시글 Controller", description = "게시글 조회, 삭제 API를 제공합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final JwtUtil jwtUtil;

    @Operation(
            summary = "게시글 작성",
            description = "게시글을 생성합니다. imageUrls에 파일 이름을 인자로 받습니다."
    )
    @ApiResponse(responseCode = "200", description = "게시글 작성 성공")
    @PostMapping
    public ResponseEntity<Void> createPost(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody PostDTO postDTO
    ) {
        String token = authorizationHeader.replace("Bearer ", "");
        Long authenticatedUserId = jwtUtil.getUserIdFromToken(token);

        System.out.println("=== Incoming createPost request ===");
        System.out.println("Authenticated userId: " + authenticatedUserId);
        System.out.println("PostDTO: " + postDTO);
        System.out.println("===================================");

        if (!authenticatedUserId.equals(postDTO.getAuthorId())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        postService.createPost(postDTO);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "게시글 이미지 삭제",
            description = "특정 게시글에서 하나의 이미지를 삭제합니다."
    )
    @DeleteMapping("/{postId}/image")
    public ResponseEntity<Void> deleteImageFromPost(
            @PathVariable UUID postId,
            @RequestBody Map<String, String> request) {
        String imageUrl = request.get("imageUrl");
        postService.deleteImage(postId, imageUrl);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "게시글 삭제",
            description = "게시글 ID를 통해 해당 게시글을 삭제합니다."
    )
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID postId) {
        postService.deletePost(postId);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "게시글 목록 조회",
            description = "가족 ID로 해당 가족의 모든 게시글을 조회합니다. categoryId를 지정하면 해당 카테고리의 게시글만 필터링됩니다. 지정하지 않으면 전체 게시글이 조회됩니다."
    )
    @ApiResponse(responseCode = "200", description = "게시글 목록 조회 성공")
    @GetMapping
    public ResponseEntity<List<PostDTO>> getPostsByFamilyAndCategory(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam("familyId") UUID familyId,
            @RequestParam(value = "categoryId", required = false) UUID categoryId
    ) {
        String token = authorizationHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(token);

        List<PostDTO> posts = postService.getPostsByFamilyAndCategory(userId, familyId, categoryId);
        return ResponseEntity.ok(posts);
    }

}
