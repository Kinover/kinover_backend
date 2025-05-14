package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.FcmToken;
import com.example.kinover_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {
    Optional<FcmToken> findByUser(User user);
}
