package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {

    // 유저 아이디로 유저 찾기
    @Lock(LockModeType.PESSIMISTIC_WRITE) // Pessimistic Write Lock
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByUserId(Long userId);

    // userId 리스트를 받아 해당하는 User 엔티티 리스트 조회
    @Query("SELECT u FROM User u WHERE u.userId IN :userIds")
    List<User> findUsersByIds(@Param("userIds") List<Long> userIds);

    // 카카오 ID로 유저 찾기
    Optional<User> findByKakaoId(Long kakaoId);

    // 랜덤 생성한 ID 중복 체크용
    boolean existsByUserId(Long userId);


//    Optional<User> findByUserId(Long kakaoId);

}
