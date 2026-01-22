// src/main/java/com/example/kinover_backend/repository/PostRepository.java
package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    // 1) family 기준 postId 목록 (createdAt desc)
    @Query("""
        select p.postId
        from Post p
        where p.family.familyId = :familyId
        order by p.createdAt desc
    """)
    List<UUID> findPostIdsByFamilyOrderByCreatedAtDesc(@Param("familyId") UUID familyId);

    // 2) family + category 기준 postId 목록 (createdAt desc)
    @Query("""
        select p.postId
        from Post p
        where p.family.familyId = :familyId
          and p.category.categoryId = :categoryId
        order by p.createdAt desc
    """)
    List<UUID> findPostIdsByFamilyAndCategoryOrderByCreatedAtDesc(
            @Param("familyId") UUID familyId,
            @Param("categoryId") UUID categoryId
    );

    // 3) ids로 posts + images fetch join (정렬은 서비스에서 복원)
    @Query("""
        select distinct p
        from Post p
        left join fetch p.images i
        where p.postId in (:ids)
    """)
    List<Post> findPostsWithImagesByIds(@Param("ids") List<UUID> ids);
}
