package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Post;
import com.example.kinover_backend.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    List<Post> findAllByFamily_FamilyIdOrderByCreatedAtDesc(UUID familyId);

    List<Post> findAllByFamily_FamilyIdAndCategory_CategoryIdOrderByCreatedAtDesc(UUID familyId, UUID categoryId);

    @Query("""
            select distinct p
            from Post p
            left join fetch p.images i
            where p.family.familyId = :familyId
            order by p.createdAt desc
            """)
    List<Post> findAllByFamilyWithImages(@Param("familyId") UUID familyId);

    @Query("""
select distinct p
from Post p
left join fetch p.images i
where p.family.familyId = :familyId
  and p.category.categoryId = :categoryId
order by p.createdAt desc
""")
List<Post> findAllByFamilyAndCategoryWithImages(@Param("familyId") UUID familyId,
                                               @Param("categoryId") UUID categoryId);

}
