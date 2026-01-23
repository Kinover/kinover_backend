package com.example.kinover_backend.controller;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.PostDTO;
import com.example.kinover_backend.dto.UpdatePostRequest;
import com.example.kinover_backend.service.PostService;
import com.example.kinover_backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "게시글 Controller", description = "게시글 조회, 삭제 API를 제공합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    private String extractBearerTokenOrNull(String authorizationHeader) {
        if (authorizationHeader == null) return null;
        String v = authorizationHeader.trim();
        if (!v.startsWith("Bearer ")) return null;
        String token = v.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    private Long extractUserIdOrUnauthorized(String authorizationHeader) {
        String token = extractBearerTokenOrNull(authorizationHeader);
        if (token == null) return null;
        return jwtUtil.getUserIdFromToken(token);
    }

    // =========================
    // ✅ CREATE (A안: familyId를 body에서 안 받음)
    // POST /api/posts
    // body: authorId, content, categoryId(or categoryTitle), imageUrls, postTypes
    // =========================
    @Operation(summary = "게시글 작성", description = "게시글을 생성합니다. (A안: 토큰 기반 familyId 결정)")
    @ApiResponse(responseCode = "200", description = "게시글 작성 성공")
    @PostMapping
    public ResponseEntity<Void> createPost(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody PostDTO postDTO
    ) {
        Long authenticatedUserId = extractUserIdOrUnauthorized(authorizationHeader);
        if (authenticatedUserId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        // ✅ authorId는 반드시 토큰 유저와 일치
        if (postDTO == null || postDTO.getAuthorId() == null || !authenticatedUserId.equals(postDTO.getAuthorId())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // ✅ A안: 서버가 familyId를 결정
        postService.createPostA(authenticatedUserId, postDTO);
        return ResponseEntity.ok().build();
    }

    // =========================
    // GET ONE (권한검증 포함)
    // =========================
    @Operation(summary = "게시글 단건 조회", description = "게시글 ID로 게시글 상세를 조회합니다. (가족 소속 권한 검증 포함)")
    @ApiResponse(responseCode = "200", description = "게시글 단건 조회 성공")
    @GetMapping("/{postId}")
    public ResponseEntity<PostDTO> getPostById(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable UUID postId
    ) {
        Long userId = extractUserIdOrUnauthorized(authorizationHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        PostDTO post = postService.getPostById(userId, postId);
        return ResponseEntity.ok(post);
    }

    // =========================
    // ✅ LIST (A안)
    // GET /api/posts?categoryId=...
    // =========================
    @Operation(summary = "게시글 목록 조회", description = "유저 토큰 기준으로 본인 가족의 게시글 목록 조회 (categoryId optional)")
    @ApiResponse(responseCode = "200", description = "게시글 목록 조회 성공")
    @GetMapping
    public ResponseEntity<List<PostDTO>> getMyFamilyPosts(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(value = "categoryId", required = false) UUID categoryId
    ) {
        Long userId = extractUserIdOrUnauthorized(authorizationHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<PostDTO> posts = postService.getMyFamilyPosts(userId, categoryId);
        return ResponseEntity.ok(posts);
    }

    // =========================
    // ✅ DELETE IMAGE (작성자만)
    // =========================
    @Operation(summary = "게시글 이미지 삭제", description = "특정 게시글에서 하나의 이미지를 삭제합니다. (작성자만 가능)")
    @DeleteMapping("/{postId}/image")
    public ResponseEntity<Void> deleteImageFromPost(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable UUID postId,
            @RequestBody(required = false) Map<String, String> request
    ) {
        Long userId = extractUserIdOrUnauthorized(authorizationHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String imageUrl = request != null ? request.get("imageUrl") : null;
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        postService.deleteImage(postId, userId, imageUrl);
        return ResponseEntity.ok().build();
    }

    // =========================
    // ✅ DELETE POST (작성자만)
    // =========================
    @Operation(summary = "게시글 삭제", description = "게시글 ID로 삭제합니다. (작성자만 가능)")
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable UUID postId
    ) {
        Long userId = extractUserIdOrUnauthorized(authorizationHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        postService.deletePost(postId, userId);
        return ResponseEntity.ok().build();
    }

    // =========================
    // ✅ POST 알림 ON/OFF (본인만 변경)
    // PATCH /api/posts/notification/post?isOn=true/false
    // =========================
    @PatchMapping("/notification/post")
    public ResponseEntity<String> updatePostNotificationSetting(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam boolean isOn
    ) {
        Long authenticatedUserId = extractUserIdOrUnauthorized(authorizationHeader);
        if (authenticatedUserId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        boolean success = userService.updatePostNotificationSetting(authenticatedUserId, isOn);
        if (success) return ResponseEntity.ok("Post notification setting updated.");
        return ResponseEntity.badRequest().body("Invalid userId");
    }

    // =========================
    // UPDATE (작성자만)
    // =========================
    @Operation(summary = "게시글 수정", description = "게시글 내용을 수정합니다. content/categoryId/imageUrls 중 필요한 것만 수정")
    @ApiResponse(responseCode = "200", description = "게시글 수정 성공")
    @PatchMapping("/{postId}")
    public ResponseEntity<Void> updatePost(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable UUID postId,
            @RequestBody(required = false) UpdatePostRequest request
    ) {
        Long authenticatedUserId = extractUserIdOrUnauthorized(authorizationHeader);
        if (authenticatedUserId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (request == null || request.getAuthorId() == null || !authenticatedUserId.equals(request.getAuthorId())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        postService.updatePost(postId, authenticatedUserId, request);
        return ResponseEntity.ok().build();
    }
}
