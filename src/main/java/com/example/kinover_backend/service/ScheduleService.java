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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;

    /**
     * 가족 ID로 전체 일정 조회
     */
    public List<ScheduleDTO> getSchedulesByFamilyId(UUID familyId) {
        List<Schedule> schedules = scheduleRepository.findByFamily_FamilyId(familyId);
        return schedules.stream()
                .map(ScheduleDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * 가족 ID + 사용자 ID로 개인 일정 조회
     */
    public List<ScheduleDTO> getSchedulesByFamilyIdAndUserId(UUID familyId, Long userId) {
        List<Schedule> schedules = scheduleRepository.findByFamily_FamilyIdAndUser_UserId(familyId, userId);
        return schedules.stream()
                .map(ScheduleDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * 일정 추가
     */
    @Transactional
    public UUID addSchedule(ScheduleDTO dto) {
        // 1. 사용자 조회
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("작성자(userId)를 찾을 수 없습니다."));

        // 2. 가족 조회
        Family family = familyRepository.findById(dto.getFamilyId())
                .orElseThrow(() -> new RuntimeException("가족(familyId)를 찾을 수 없습니다."));

        // 3. Schedule 엔티티 생성 및 매핑
        Schedule schedule = new Schedule();
        schedule.setTitle(dto.getTitle());
        schedule.setMemo(dto.getMemo());
        schedule.setDate(dto.getDate());
        schedule.setPersonal(dto.isPersonal());
        schedule.setUser(user);
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
}
