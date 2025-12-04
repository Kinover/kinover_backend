package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.CategoryDTO;
import com.example.kinover_backend.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "카테고리 Controller", description = "카테고리 조회, 추가 API를 제공합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<CategoryDTO> createCategory(@RequestBody CategoryDTO dto) {
        CategoryDTO created = categoryService.createCategory(dto); // 저장 후 DTO 리턴
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{familyId}")
    public ResponseEntity<List<CategoryDTO>> getCategories(@PathVariable UUID familyId) {
        return ResponseEntity.ok(categoryService.getCategories(familyId));
    }
}
