package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Memory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    // 가족 아이디로 추억 찾기
    @Query("SELECT m FROM Memory m WHERE m.family.familyId=:familyId")
    Optional<List<Memory>> findByFamilyId(@Param("familyId") UUID familyId);

    // 추억 아이디로 추억 찾기
    @Query("SELECT m FROM Memory m WHERE m.memoryId=:memoryId")
    Optional<List<Memory>> findByMemoryId(@Param("memoryId") UUID memoryId);

}
