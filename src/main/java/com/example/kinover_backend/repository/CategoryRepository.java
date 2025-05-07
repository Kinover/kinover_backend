package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Category;
import com.example.kinover_backend.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByFamily(Family family);
}
