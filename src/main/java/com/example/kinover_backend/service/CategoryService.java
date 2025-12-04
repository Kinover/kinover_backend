package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.CategoryDTO;
import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.entity.Category;
import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.repository.CategoryRepository;
import com.example.kinover_backend.repository.FamilyRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

        private final CategoryRepository categoryRepository;
        private final FamilyRepository familyRepository;

        @Transactional
        public CategoryDTO createCategory(CategoryDTO dto) {
                Family family = familyRepository.findById(dto.getFamilyId())
                                .orElseThrow(() -> new RuntimeException("가족 없음"));

                Category category = new Category();
                // 🔸 프론트에서 만든 UUID 그대로 쓰는 경우
                category.setCategoryId(dto.getCategoryId());
                category.setTitle(dto.getTitle());
                category.setFamily(family);

                Category saved = categoryRepository.save(category);

                // 🔥 저장된 엔티티 기준으로 DTO 만들어서 반환
                return new CategoryDTO(
                                saved.getCategoryId(),
                                saved.getFamily().getFamilyId(),
                                saved.getTitle(),
                                saved.getCreatedAt());
        }

        public List<CategoryDTO> getCategories(UUID familyId) {
                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new RuntimeException("가족 없음"));

                return categoryRepository.findByFamily(family)
                                .stream().map(category -> new CategoryDTO(
                                                category.getCategoryId(),
                                                category.getFamily().getFamilyId(),
                                                category.getTitle(),
                                                category.getCreatedAt()))
                                .collect(Collectors.toList());
        }
}
