package com.example.kinover_backend.repository;

import com.example.kinover_backend.dto.CommentDTO;
import com.example.kinover_backend.entity.Comment;
import com.example.kinover_backend.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    List<Comment> findByPostOrderByCreatedAtAsc(Post post);
    void deleteAllByPost(Post post);

    @Query("""
        select new com.example.kinover_backend.dto.CommentDTO(
            c.commentId,
            c.post.postId,
            c.content,
            a.userId,
            a.name,
            a.image,
            c.createdAt
        )
        from Comment c
        left join c.author a
        where c.post.postId = :postId
          and (c.hidden is null or c.hidden = false)
          and (
            :viewerId is null
            or not exists (
              select 1 from UserBlock ub
              where ub.blocker.userId = :viewerId
                and ub.blocked.userId = a.userId
            )
          )
        order by c.createdAt asc
    """)
    List<CommentDTO> findCommentDtosByPostIdVisibleForViewerOrderByCreatedAtAsc(
            @Param("postId") UUID postId,
            @Param("viewerId") Long viewerId
    );

    // ✅ 해당 게시글에 댓글 단 사람들(중복 제거)
    @Query("select distinct c.author.userId from Comment c where c.post.postId = :postId")
    List<Long> findDistinctAuthorIdsByPostId(@Param("postId") UUID postId);
}
