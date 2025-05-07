package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Post;
import com.example.kinover_backend.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    // 특정 가족의 게시물만 조회 (최신순)
    List<Post> findAllByFamilyOrderByCreatedAtDesc(Family family);
}
