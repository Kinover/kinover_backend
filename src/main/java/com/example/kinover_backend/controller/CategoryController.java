package com.example.kinover_backend.controller;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.CategoryDTO;
import com.example.kinover_backend.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "카테고리 Controller", description = "카테고리 조회, 추가 API를 제공합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final JwtUtil jwtUtil;

    private String extractBearerTokenOrNull(String authorizationHeader) {
        if (authorizationHeader == null) return null;
        String v = authorizationHeader.trim();
        if (!v.startsWith("Bearer ")) return null;
        String token = v.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    private Long extractUserIdOrNull(String authorizationHeader) {
        String token = extractBearerTokenOrNull(authorizationHeader);
        if (token == null) return null;

        try {
            return jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            // 토큰 만료/서명오류/형식오류 등 -> 401로 처리
            return null;
        }
    }

    @PostMapping
    public ResponseEntity<CategoryDTO> createCategory(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody CategoryDTO dto
    ) {
        Long userId = extractUserIdOrNull(authorizationHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            CategoryDTO created = categoryService.createCategoryA(userId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (RuntimeException e) {
            // "가족 없음", "가족 소속 없음" 같은 케이스를 404/400 중 하나로 정해야 함
            // 여기서는 404로 처리(가족 리소스를 못 찾음)
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getCategories(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = extractUserIdOrNull(authorizationHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            return ResponseEntity.ok(categoryService.getCategoriesA(userId));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
