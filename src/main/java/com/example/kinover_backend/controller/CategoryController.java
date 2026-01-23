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

    private Long extractUserIdOrUnauthorized(String authorizationHeader) {
        String token = extractBearerTokenOrNull(authorizationHeader);
        if (token == null) return null;
        return jwtUtil.getUserIdFromToken(token);
    }

    /**
     * ✅ A안: 서버가 토큰으로 familyId 결정
     * POST /api/categories
     * body: { title }
     */
    @PostMapping
    public ResponseEntity<CategoryDTO> createCategory(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody CategoryDTO dto
    ) {
        Long userId = extractUserIdOrUnauthorized(authorizationHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        CategoryDTO created = categoryService.createCategoryA(userId, dto);
        return ResponseEntity.ok(created);
    }

    /**
     * ✅ A안: 서버가 토큰으로 familyId 결정
     * GET /api/categories
     */
    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getCategories(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = extractUserIdOrUnauthorized(authorizationHeader);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        return ResponseEntity.ok(categoryService.getCategoriesA(userId));
    }
}
