// src/main/java/com/example/kinover_backend/controller/PostController.java
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

    // =========================
    // ✅ 공통: Authorization 헤더 방어
    // - null/NPE로 500 나지 않게
    // - Bearer 형식 아니면 401로 명확히
    // =========================
    private String extractBearerTokenOrNull(String authorizationHeader) {
        if (authorizationHeader == null) return null;
        String v = authorizationHeader.trim();
        if (!v.startsWith("Bearer ")) return null;
        String token = v.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    @Operation(
            summary = "게시글 작성",
            description = "게시글을 생성합니다. imageUrls에 파일 이름을 인자로 받습니다."
    )
    @ApiResponse(responseCode = "200", description = "게시글 작성 성공")
    @PostMapping
    public ResponseEntity<Void> createPost(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody PostDTO postDTO
    ) {
        String token = extractBearerTokenOrNull(authorizationHeader);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long authenticatedUserId = jwtUtil.getUserIdFromToken(token);

        if (postDTO == null || postDTO.getAuthorId() == null || !authenticatedUserId.equals(postDTO.getAuthorId())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        postService.createPost(postDTO);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "게시글 단건 조회",
            description = "게시글 ID로 게시글 상세를 조회합니다. (가족 소속 권한 검증 포함)"
    )
    @ApiResponse(responseCode = "200", description = "게시글 단건 조회 성공")
    @GetMapping("/{postId}")
    public ResponseEntity<PostDTO> getPostById(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable UUID postId
    ) {
        String token = extractBearerTokenOrNull(authorizationHeader);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long userId = jwtUtil.getUserIdFromToken(token);

        PostDTO post = postService.getPostById(userId, postId);
        return ResponseEntity.ok(post);
    }

    @Operation(
            summary = "게시글 이미지 삭제",
            description = "특정 게시글에서 하나의 이미지를 삭제합니다."
    )
    @DeleteMapping("/{postId}/image")
    public ResponseEntity<Void> deleteImageFromPost(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable UUID postId,
            @RequestBody Map<String, String> request
    ) {
        // ✅ 이 API는 기존엔 토큰 검증이 아예 없었음 (누구나 삭제 가능 상태)
        // ✅ 최소한 401 방어라도 넣어두자
        String token = extractBearerTokenOrNull(authorizationHeader);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        jwtUtil.getUserIdFromToken(token); // 토큰 유효성 확인용(결과는 현재 로직에서 미사용)

        String imageUrl = request != null ? request.get("imageUrl") : null;
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        postService.deleteImage(postId, imageUrl);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "게시글 삭제",
            description = "게시글 ID를 통해 해당 게시글을 삭제합니다."
    )
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable UUID postId
    ) {
        // ✅ 이 API도 기존엔 토큰 검증이 아예 없었음 (누구나 삭제 가능 상태)
        String token = extractBearerTokenOrNull(authorizationHeader);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        jwtUtil.getUserIdFromToken(token);

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
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam("familyId") UUID familyId,
            @RequestParam(value = "categoryId", required = false) UUID categoryId
    ) {
        String token = extractBearerTokenOrNull(authorizationHeader);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long userId = jwtUtil.getUserIdFromToken(token);

        List<PostDTO> posts = postService.getPostsByFamilyAndCategory(userId, familyId, categoryId);
        return ResponseEntity.ok(posts);
    }

    @PatchMapping("/notification/post")
    @Operation(summary = "게시글 알림 ON/OFF", description = "userId와 상태(true/false)를 전달하여 게시글 알림을 켜거나 끕니다.")
    public ResponseEntity<String> updatePostNotificationSetting(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam Long userId,
            @RequestParam boolean isOn
    ) {
        // ✅ 이 API도 최소한 토큰 없으면 401
        String token = extractBearerTokenOrNull(authorizationHeader);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        jwtUtil.getUserIdFromToken(token);

        boolean success = userService.updatePostNotificationSetting(userId, isOn);
        if (success) return ResponseEntity.ok("Post notification setting updated.");
        else return ResponseEntity.badRequest().body("Invalid userId");
    }

    @Operation(
            summary = "게시글 수정",
            description = "게시글 내용을 수정합니다. content/categoryId/imageUrls 중 필요한 것만 수정할 수 있습니다."
    )
    @ApiResponse(responseCode = "200", description = "게시글 수정 성공")
    @PatchMapping("/{postId}")
    public ResponseEntity<Void> updatePost(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable UUID postId,
            @RequestBody UpdatePostRequest request
    ) {
        String token = extractBearerTokenOrNull(authorizationHeader);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long authenticatedUserId = jwtUtil.getUserIdFromToken(token);

        // ✅ 작성자 검증
        if (request == null || request.getAuthorId() == null || !authenticatedUserId.equals(request.getAuthorId())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        postService.updatePost(postId, authenticatedUserId, request);
        return ResponseEntity.ok().build();
    }
}
