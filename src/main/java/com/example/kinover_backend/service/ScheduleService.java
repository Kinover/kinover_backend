package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.ScheduleDTO;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.Schedule;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.repository.ScheduleRepository;
import com.example.kinover_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScheduleService {
    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final UserFamilyService userFamilyService;

    @Autowired
    public ScheduleService(ScheduleRepository scheduleRepository, UserService userService, UserRepository userRepository, UserFamilyService userFamilyService) {
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.userFamilyService = userFamilyService;
    }

    public Optional<List<ScheduleDTO>> getSchedulesForFamilyAndDate(UUID familyId, String date) {
        List<UserDTO> userFamilies = userFamilyService.getUsersByFamilyId(familyId);
        LocalDate localDate = LocalDate.parse(date);

        List<ScheduleDTO> scheduleDTOList = new ArrayList<>();

        // ✅ 가족 공용 일정은 한 번만 가져오기
        List<Schedule> familySchedules = scheduleRepository.findSchedulesByFamilyIdAndDate(familyId, localDate);
        for (Schedule schedule : familySchedules) {
            scheduleDTOList.add(new ScheduleDTO(schedule));
        }

        // ✅ 개별 사용자 일정 가져오기 (공용 일정 제외)
        for (UserDTO user : userFamilies) {
            List<Schedule> userSchedules = scheduleRepository.findSchedulesByFamilyIdAndUserIdOnly(familyId, user.getUserId(), localDate);
            for (Schedule schedule : userSchedules) {
                scheduleDTOList.add(new ScheduleDTO(schedule));
            }
        }

        return Optional.of(scheduleDTOList);
    }


    // 유저별 날짜별 스케줄 조회
    public Optional<List<ScheduleDTO>> getSchedulesForUserAndDate(UUID familyId, Long userId, String date) {
        // 유효한 유저가 있는지 확인
        LocalDate localDate = LocalDate.parse(date); // String -> LocalDate 변환

        if (userRepository.existsById(userId)) {
            // familyId, userId, localDate에 맞는 스케줄 조회
            List<Schedule> schedules = scheduleRepository.findSchedulesByFamilyIdAndUserIdAndDate(familyId, userId, localDate);

            // ScheduleDTO로 변환
            List<ScheduleDTO> scheduleDTOs = schedules.stream()
                    .map(schedule -> new ScheduleDTO(schedule)) // Schedule을 ScheduleDTO로 변환
                    .collect(Collectors.toList());

            return Optional.of(scheduleDTOs); // List<ScheduleDTO> 반환
        }

        throw new RuntimeException("User not found");
    }


    // 일정 추가
    public void addSchedule(Schedule schedule) {
        this.scheduleRepository.save(schedule);
    }

    // 일정 삭제
    public void removeSchedule(UUID scheduleId) {
        this.scheduleRepository.deleteById(scheduleId);
    }

    // 일정 삭제
    public void modifySchedule(Schedule schedule) {
        this.scheduleRepository.save(schedule);
    }

}
