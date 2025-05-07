package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Comment;
import com.example.kinover_backend.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    List<Comment> findByPostOrderByCreatedAtAsc(Post post);
}
