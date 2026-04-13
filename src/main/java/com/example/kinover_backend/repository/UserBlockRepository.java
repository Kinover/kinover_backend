package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {

    boolean existsByBlocker_UserIdAndBlocked_UserId(Long blockerId, Long blockedId);

    void deleteByBlocker_UserIdAndBlocked_UserId(Long blockerId, Long blockedId);

    @Query("select ub.blocked.userId from UserBlock ub where ub.blocker.userId = :blockerId")
    List<Long> findBlockedUserIdsByBlockerId(@Param("blockerId") Long blockerId);
}
