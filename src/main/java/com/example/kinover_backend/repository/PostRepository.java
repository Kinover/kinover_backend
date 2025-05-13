package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Post;
import com.example.kinover_backend.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    List<Post> findAllByFamily_FamilyIdOrderByCreatedAtDesc(UUID familyId);

    List<Post> findAllByFamily_FamilyIdAndCategory_CategoryIdOrderByCreatedAtDesc(UUID familyId, UUID categoryId);

}
