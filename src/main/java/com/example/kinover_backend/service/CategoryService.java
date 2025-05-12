package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.CategoryDTO;
import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.entity.Category;
import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.repository.CategoryRepository;
import com.example.kinover_backend.repository.FamilyRepository;
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

    public void createCategory(CategoryDTO dto) {
        Family family = familyRepository.findById(dto.getFamilyId())
                .orElseThrow(() -> new RuntimeException("가족 없음"));

        Category category = new Category();
        category.setCategoryId(UUID.randomUUID());
        category.setTitle(dto.getTitle());
        category.setFamily(family);

        categoryRepository.save(category);
    }

    public List<CategoryDTO> getCategories(UUID familyId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new RuntimeException("가족 없음"));

        return categoryRepository.findByFamily(family)
                .stream().map(category -> new CategoryDTO(
                        category.getCategoryId(),
                        category.getFamily().getFamilyId(),
                        category.getTitle(),
                        category.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }
}
