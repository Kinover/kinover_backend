// src/main/java/com/example/kinover_backend/repository/ScheduleRepository.java
package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    List<Schedule> findByFamily_FamilyIdAndDate(UUID familyId, LocalDate date);

    List<Schedule> findByFamily_FamilyIdAndDateBetween(UUID familyId, LocalDate start, LocalDate end);

    /**
     * ✅ userId 기준 조회
     * - FAMILY/ANNIVERSARY: 모두에게 노출
     * - INDIVIDUAL: participants에 userId 포함된 것만 노출
     */
    @Query("""
        select distinct s
        from Schedule s
        left join s.participants p
        where s.family.familyId = :familyId
          and s.date = :date
          and (s.hidden is null or s.hidden = false)
          and (
            :userId is null
            or s.type in ('FAMILY', 'ANNIVERSARY')
            or p.userId = :userId
          )
          and (
            :viewerId is null
            or s.createdBy is null
            or not exists (
              select 1 from UserBlock ub
              where ub.blocker.userId = :viewerId
                and ub.blocked.userId = s.createdBy.userId
            )
          )
        """)
    List<Schedule> findVisibleSchedulesByFilter(
        @Param("familyId") UUID familyId,
        @Param("date") LocalDate date,
        @Param("userId") Long userId,
        @Param("viewerId") Long viewerId
    );

    List<Schedule> findByFamily_FamilyIdAndDateBetweenAndHiddenFalse(
            UUID familyId,
            LocalDate start,
            LocalDate end
    );
}
