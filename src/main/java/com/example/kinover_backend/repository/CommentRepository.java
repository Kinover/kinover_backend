package com.example.kinover_backend.repository;

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

    // ✅ 해당 게시글에 댓글 단 사람들(중복 제거)
    @Query("select distinct c.author.userId from Comment c where c.post.postId = :postId")
    List<Long> findDistinctAuthorIdsByPostId(@Param("postId") UUID postId);
}
