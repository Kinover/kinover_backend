package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.ScheduleDTO;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.entity.Schedule;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.repository.FamilyRepository;
import com.example.kinover_backend.repository.ScheduleRepository;
import com.example.kinover_backend.repository.UserRepository;
import com.example.kinover_backend.repository.FamilyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;
    private final UserFamilyService userFamilyService;

    /**
     * 가족 전체 일정 조회 (공용 + 개인 일정 포함)
     */
    public Optional<List<ScheduleDTO>> getSchedulesForFamilyAndDate(UUID familyId, String date) {
        LocalDate localDate = LocalDate.parse(date);
        List<ScheduleDTO> scheduleDTOList = new ArrayList<>();

        // 공용 일정
        List<Schedule> familySchedules = scheduleRepository.findSchedulesByFamilyIdAndDate(familyId, localDate);
        scheduleDTOList.addAll(familySchedules.stream()
                .map(ScheduleDTO::new)
                .collect(Collectors.toList()));

        // 개인 일정
        List<UserDTO> userFamilies = userFamilyService.getUsersByFamilyId(familyId);
        for (UserDTO user : userFamilies) {
            List<Schedule> userSchedules = scheduleRepository.findSchedulesByFamilyIdAndUserIdOnly(
                    familyId, user.getUserId(), localDate);
            scheduleDTOList.addAll(userSchedules.stream()
                    .map(ScheduleDTO::new)
                    .collect(Collectors.toList()));
        }

        return Optional.of(scheduleDTOList);
    }

    /**
     * 사용자 개인 일정 조회
     */
    public Optional<List<ScheduleDTO>> getSchedulesForUserAndDate(UUID familyId, Long userId, String date) {
        LocalDate localDate = LocalDate.parse(date);

        if (!userRepository.existsById(userId)) {
            throw new RuntimeException("사용자를 찾을 수 없습니다.");
        }

        List<Schedule> schedules = scheduleRepository.findSchedulesByFamilyIdAndUserIdAndDate(
                familyId, userId, localDate);

        List<ScheduleDTO> dtoList = schedules.stream()
                .map(ScheduleDTO::new)
                .collect(Collectors.toList());

        return Optional.of(dtoList);
    }

    /**
     * 일정 추가
     */
    @Transactional
    public UUID addSchedule(ScheduleDTO dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        Family family = familyRepository.findById(dto.getFamilyId())
                .orElseThrow(() -> new RuntimeException("가족 정보를 찾을 수 없습니다."));

        Schedule schedule = new Schedule();
        schedule.setScheduleId(UUID.randomUUID());
        schedule.setTitle(dto.getTitle());
        schedule.setMemo(dto.getMemo());
        schedule.setPersonal(dto.isPersonal());
        schedule.setDate(dto.getDate());
        schedule.setUser(user);
        schedule.setFamily(family);

        scheduleRepository.save(schedule);
        return schedule.getScheduleId();
    }

    /**
     * 일정 삭제
     */
    @Transactional
    public void removeSchedule(UUID scheduleId) {
        scheduleRepository.deleteById(scheduleId);
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
        schedule.setPersonal(dto.isPersonal());
        schedule.setDate(dto.getDate());

        scheduleRepository.save(schedule);
        return schedule.getScheduleId();
    }
}