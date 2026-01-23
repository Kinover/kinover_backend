package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.entity.UserFamily;
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

    boolean existsByUser_UserId(Long userId);

    List<UserFamily> findAllByUser_UserId(Long userId);

    @Query("SELECT uf.user FROM UserFamily uf WHERE uf.family.familyId = :familyId")
    List<User> findUsersByFamilyId(@Param("familyId") UUID familyId);

    // userId로 속한 모든 Family 조회
    @Query("SELECT uf.family FROM UserFamily uf WHERE uf.user.userId = :userId")
    List<Family> findFamiliesByUserId(@Param("userId") Long userId);

    Optional<UserFamily> findByUser_UserIdAndFamily_FamilyId(Long userId, UUID familyId);

    // ✅ 게시글/가족 권한 체크용 (가족 소속 여부)
    boolean existsByUser_UserIdAndFamily_FamilyId(Long userId, UUID familyId);

    // ✅ A안 핵심: "유저가 속한 가족 ID 조회" (유저 1가족 전제)
    @Query("""
        select uf.family.familyId
        from UserFamily uf
        where uf.user.userId = :userId
    """)
    List<UUID> findFamilyIdsByUserId(@Param("userId") Long userId);
}
