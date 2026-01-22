// src/main/java/com/example/kinover_backend/repository/PostRepository.java
package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    // (옵션) 기존 메서드 유지해도 되지만, 목록 조회는 아래 2-step 방식 권장
    List<Post> findAllByFamily_FamilyIdOrderByCreatedAtDesc(UUID familyId);

    List<Post> findAllByFamily_FamilyIdAndCategory_CategoryIdOrderByCreatedAtDesc(UUID familyId, UUID categoryId);

    // ✅ 1) 정렬된 ID만 먼저 뽑기 (가족 전체)
    @Query("""
        select p.postId
        from Post p
        where p.family.familyId = :familyId
        order by p.createdAt desc
    """)
    List<UUID> findPostIdsByFamilyOrderByCreatedAtDesc(@Param("familyId") UUID familyId);

    // ✅ 1) 정렬된 ID만 먼저 뽑기 (가족 + 카테고리)
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

    // ✅ 2) IDs로 posts + images fetch (여기서는 정렬 하지 말고, 서비스에서 복원)
    @Query("""
        select distinct p
        from Post p
        left join fetch p.images i
        where p.postId in :ids
    """)
    List<Post> findPostsWithImagesByIds(@Param("ids") List<UUID> ids);
}
