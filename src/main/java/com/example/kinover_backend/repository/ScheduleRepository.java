package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    // schedule 아이디 통해
    @Query("SELECT s FROM Schedule s WHERE s.scheduleId= :scheduleId")
    Schedule findByScheduleId(@Param("scheduleId") Long scheduleId);

    // family 아이디 통해
    @Query("SELECT s FROM Schedule s WHERE s.family.familyId= :familyId")
    Optional<List<Schedule>> findByFamilyId(@Param("familyId") UUID familyId);

//    @Query("SELECT s FROM Schedule s WHERE s.user.userId= :userId")
//    Optional<List<Schedule>> findSchedulesByUserIdAndDate(@Param("userId") Long userId,@Param("localDate") Date localDate);

    @Query("SELECT DISTINCT s FROM Schedule s WHERE (s.user.userId = :userId OR s.user.userId IS NULL) AND s.date = :date AND s.family.familyId = :familyId")
    List<Schedule> findSchedulesByFamilyIdAndUserIdAndDate(@Param("familyId") UUID familyId, @Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("SELECT s FROM Schedule s WHERE s.family.familyId = :familyId AND s.date = :date AND s.user.userId IS NULL")
    List<Schedule> findSchedulesByFamilyIdAndDate(@Param("familyId") UUID familyId, @Param("date") LocalDate date);

    @Query("SELECT s FROM Schedule s WHERE s.family.familyId = :familyId AND s.user.userId = :userId AND s.date = :date")
    List<Schedule> findSchedulesByFamilyIdAndUserIdOnly(@Param("familyId") UUID familyId, @Param("userId") Long userId, @Param("date") LocalDate date);

}
