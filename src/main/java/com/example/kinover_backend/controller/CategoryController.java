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
    public ResponseEntity<Void> createCategory(@RequestBody CategoryDTO dto) {
        categoryService.createCategory(dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{familyId}")
    public ResponseEntity<List<CategoryDTO>> getCategories(@PathVariable UUID familyId) {
        return ResponseEntity.ok(categoryService.getCategories(familyId));
    }
}
