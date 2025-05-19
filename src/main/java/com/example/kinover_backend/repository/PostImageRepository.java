package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Post;
import com.example.kinover_backend.entity.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PostImageRepository extends JpaRepository<PostImage, UUID> {
    void deleteAllByPost(Post post);
}