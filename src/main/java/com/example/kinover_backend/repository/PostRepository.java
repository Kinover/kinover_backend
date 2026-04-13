package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    // ✅ family 기준: posts + images 한 번에 로딩
    @Query("""
        select distinct p
        from Post p
        left join fetch p.images i
        where p.family.familyId = :familyId
          and (p.hidden is null or p.hidden = false)
          and not exists (
            select 1 from UserBlock ub
            where ub.blocker.userId = :viewerId
              and ub.blocked.userId = p.author.userId
          )
        order by p.createdAt desc
    """)
    List<Post> findByFamilyWithImagesVisibleForViewerOrderByCreatedAtDesc(
            @Param("familyId") UUID familyId,
            @Param("viewerId") Long viewerId
    );

    // ✅ family + category 기준: posts + images 한 번에 로딩
    @Query("""
        select distinct p
        from Post p
        left join fetch p.images i
        where p.family.familyId = :familyId
          and p.category.categoryId = :categoryId
          and (p.hidden is null or p.hidden = false)
          and not exists (
            select 1 from UserBlock ub
            where ub.blocker.userId = :viewerId
              and ub.blocked.userId = p.author.userId
          )
        order by p.createdAt desc
    """)
    List<Post> findByFamilyAndCategoryWithImagesVisibleForViewerOrderByCreatedAtDesc(
            @Param("familyId") UUID familyId,
            @Param("categoryId") UUID categoryId,
            @Param("viewerId") Long viewerId
    );

    // ✅ 추가: 특정 카테고리에 속한 게시글 개수 세기
    long countByCategory_CategoryId(UUID categoryId);
}
