package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    /**
     * 특정 가족의 모든 일정 조회
     */
    List<Schedule> findByFamily_FamilyId(UUID familyId);

    /**
     * 특정 가족 + 특정 사용자에 대한 일정 조회
     */
    List<Schedule> findByFamily_FamilyIdAndUser_UserId(UUID familyId, Long userId);

    List<Schedule> findByFamily_FamilyIdAndDate(UUID familyId, LocalDate date);

    List<Schedule> findByFamily_FamilyIdAndUser_UserIdAndDate(UUID familyId, Long userId, LocalDate date);

}
