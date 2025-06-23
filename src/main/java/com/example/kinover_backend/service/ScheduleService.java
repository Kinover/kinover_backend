package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.ScheduleDTO;
import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.entity.Schedule;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.repository.FamilyRepository;
import com.example.kinover_backend.repository.ScheduleRepository;
import com.example.kinover_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;

    @Transactional(readOnly = true)
    public List<ScheduleDTO> getSchedulesByFilter(ScheduleDTO dto) {
        UUID familyId = dto.getFamilyId();
        LocalDate date = dto.getDate();
        Long userId = dto.getUserId();

        List<Schedule> schedules;

        if (userId != null) {
            schedules = scheduleRepository.findByFamily_FamilyIdAndUser_UserIdAndDate(familyId, userId, date);
        } else {
            schedules = scheduleRepository.findByFamily_FamilyIdAndDate(familyId, date);
        }

        return schedules.stream().map(ScheduleDTO::new).toList();
    }

    /**
     * 일정 추가
     */
    @Transactional
    public UUID addSchedule(ScheduleDTO dto) {

        // 가족 조회
        Family family = familyRepository.findById(dto.getFamilyId())
                .orElseThrow(() -> new RuntimeException("가족(familyId)를 찾을 수 없습니다."));

        // Schedule 엔티티 생성 및 매핑
        Schedule schedule = new Schedule();
        if (dto.isPersonal()) {
            if (dto.getUserId() == null) {
                throw new IllegalArgumentException("Personal schedule must include a userId.");
            }
            User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("작성자(userId)를 찾을 수 없습니다."));
            schedule.setUser(user);
        } else {
            schedule.setUser(null); // 공동일정일 경우 user 없음
        }

        schedule.setTitle(dto.getTitle());
        schedule.setMemo(dto.getMemo());
        schedule.setDate(dto.getDate());
        schedule.setPersonal(dto.isPersonal());
        schedule.setFamily(family);

        // 4. 저장 및 ID 반환
        scheduleRepository.save(schedule);
        return schedule.getScheduleId();
    }

    /**
     * 일정 수정
     */
    @Transactional
    public UUID modifySchedule(ScheduleDTO dto) {
        Schedule schedule = scheduleRepository.findById(dto.getScheduleId())
                .orElseThrow(() -> new RuntimeException("수정할 일정을 찾을 수 없습니다."));

        schedule.setTitle(dto.getTitle());
        schedule.setMemo(dto.getMemo());
        schedule.setDate(dto.getDate());

        return schedule.getScheduleId();
    }

    /**
     * 일정 삭제
     */
    @Transactional
    public void removeSchedule(UUID scheduleId) {
        scheduleRepository.deleteById(scheduleId);
    }

    public Map<Integer, Long> getScheduleCountPerDay(UUID familyId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<Schedule> schedules = scheduleRepository.findByFamily_FamilyIdAndDateBetween(familyId, startDate, endDate);

        return schedules.stream()
            .collect(Collectors.groupingBy(
                schedule -> schedule.getDate().getDayOfMonth(),
                Collectors.counting()
            ));
    }

}
