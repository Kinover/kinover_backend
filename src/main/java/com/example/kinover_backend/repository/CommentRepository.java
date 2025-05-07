package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Comment;
import com.example.kinover_backend.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostOrderByCreatedAtAsc(Post post);
}
