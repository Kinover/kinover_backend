package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Comment;
import com.example.kinover_backend.entity.Memory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    // 추억 아이디로 해당 추억에 해당되는 모든 댓글 조회
    @Query("SELECT c FROM Comment c WHERE c.memory.memoryId= :memoryId")
    Optional<List<Comment>> findByMemoryId(@Param("memoryId") UUID memoryId);
}
