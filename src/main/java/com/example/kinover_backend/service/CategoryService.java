package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.CategoryDTO;
import com.example.kinover_backend.entity.Category;
import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.repository.CategoryRepository;
import com.example.kinover_backend.repository.FamilyRepository;
import com.example.kinover_backend.repository.UserFamilyRepository;
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
    private final UserFamilyRepository userFamilyRepository;

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * ✅ A안 핵심: userId -> familyId 1개 결정 (유저 1가족 전제)
     * - 만약 여러 가족이 나오면 지금은 에러로 막는 편이 안전함
     */
    private UUID resolveSingleFamilyIdOrThrow(Long userId) {
        if (userId == null) throw new IllegalArgumentException("userId is null");

        List<UUID> familyIds = userFamilyRepository.findFamilyIdsByUserId(userId);
        if (familyIds == null || familyIds.isEmpty()) {
            throw new RuntimeException("가족 소속이 없습니다.");
        }

        // ✅ 다가족 데이터가 생겼을 때 “조용히 첫번째로 처리”하면 사고 나기 쉬움
        if (familyIds.size() > 1) {
            throw new RuntimeException("여러 가족에 소속되어 있습니다. active family 설정이 필요합니다.");
        }

        return familyIds.get(0);
    }

    // =========================================================
    // ✅ 기존(B안) 유지: familyId를 클라가 주는 방식
    // =========================================================
    @Transactional
    public CategoryDTO createCategory(CategoryDTO dto) {
        if (dto == null) throw new IllegalArgumentException("dto is null");
        if (dto.getFamilyId() == null) throw new IllegalArgumentException("familyId is null");
        if (isBlank(dto.getTitle())) throw new IllegalArgumentException("title is blank");

        Family family = familyRepository.findById(dto.getFamilyId())
                .orElseThrow(() -> new RuntimeException("가족 없음"));

        Category category = new Category();
        category.setTitle(dto.getTitle());
        category.setFamily(family);

        Category saved = categoryRepository.save(category);

        return new CategoryDTO(
                saved.getCategoryId(),
                saved.getFamily().getFamilyId(),
                saved.getTitle(),
                saved.getCreatedAt()
        );
    }

    public List<CategoryDTO> getCategories(UUID familyId) {
        if (familyId == null) throw new IllegalArgumentException("familyId is null");

        // ✅ 변경된 Repository 메소드 호출 (OrderByCreatedAtDesc)
        return categoryRepository.findByFamily_FamilyIdOrderByCreatedAtDesc(familyId)
                .stream()
                .map(category -> new CategoryDTO(
                        category.getCategoryId(),
                        category.getFamily().getFamilyId(),
                        category.getTitle(),
                        category.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    // =========================================================
    // ✅ A안: 토큰(userId) 기반
    // =========================================================
    @Transactional
    public CategoryDTO createCategoryA(Long userId, CategoryDTO dto) {
        if (dto == null) throw new IllegalArgumentException("dto is null");
        if (isBlank(dto.getTitle())) throw new IllegalArgumentException("title is blank");

        UUID familyId = resolveSingleFamilyIdOrThrow(userId);

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new RuntimeException("가족 없음"));

        Category category = new Category();
        category.setTitle(dto.getTitle());
        category.setFamily(family);

        Category saved = categoryRepository.save(category);

        return new CategoryDTO(
                saved.getCategoryId(),
                saved.getFamily().getFamilyId(),
                saved.getTitle(),
                saved.getCreatedAt()
        );
    }

    public List<CategoryDTO> getCategoriesA(Long userId) {
        UUID familyId = resolveSingleFamilyIdOrThrow(userId);
        return getCategories(familyId);
    }
}
