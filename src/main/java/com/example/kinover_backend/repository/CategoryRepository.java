package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findByFamily_FamilyId(UUID familyId);
    // 뒤에 OrderByCreatedAtDesc 를 붙입니다.
    List<Category> findByFamily_FamilyIdOrderByCreatedAtDesc(UUID familyId);
}
