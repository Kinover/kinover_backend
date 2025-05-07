package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.UserFamily;
import com.example.kinover_backend.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserFamilyRepository extends JpaRepository<UserFamily, UUID> {

    // 특정 familyId를 가진 모든 UserFamily 엔티티에서 userId만 가져오기 (성능 최적화)
    @Query("SELECT uf.user.userId FROM UserFamily uf WHERE uf.family.familyId = :familyId")
    List<Long> findUserIdsByFamilyId(@Param("familyId") UUID familyId);

    // 특정 familyId에 속해있는 특정 user 삭제
    @Transactional
    @Modifying
    @Query("DELETE FROM UserFamily uf WHERE uf.family.familyId = :familyId AND uf.user.userId = :userId")
    void deleteUserByFamilyIdAndUserId(@Param("familyId") UUID familyId, @Param("userId") Long userId);

    // 특정 familyId에 속해있는 특정 user 추가 → save() 메서드 사용 권장
    default UserFamily addUserByFamilyIdAndUserId(UserFamily userFamily) {
        return save(userFamily);
    }

    @Query("SELECT uf.family FROM UserFamily uf WHERE uf.user.userId = :userId")
    Optional<Family> findFamilyByUserId(@Param("userId") Long userId);
}
