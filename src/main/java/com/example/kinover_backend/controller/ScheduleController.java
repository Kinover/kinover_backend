package com.example.kinover_backend.controller;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.ScheduleDTO;
import com.example.kinover_backend.service.ScheduleService;
import com.example.kinover_backend.service.UserFamilyService;
import com.example.kinover_backend.service.UserService;
import com.example.kinover_backend.entity.Schedule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "일정 Controller", description = "일정 관련 API를 제공합니다.")
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final UserFamilyService userFamilyService;
    private final ScheduleService scheduleService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Autowired
    public ScheduleController(
            UserFamilyService userFamilyService,
            ScheduleService scheduleService,
            UserService userService,
            JwtUtil jwtUtil) {
        this.userFamilyService = userFamilyService;
        this.scheduleService = scheduleService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @Operation(summary = "가족 일정 조회 (특정 날짜)", description = "가족 ID와 날짜를 기준으로 해당 일정을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "일정 조회 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = Schedule.class))))
    @PostMapping("/get/{familyId}")
    public Optional<List<ScheduleDTO>> getSchedulesForFamilyAndDate(
            @RequestHeader("Authorization") String authorizationHeader,
            @Parameter(description = "가족 ID", required = true)
            @PathVariable UUID familyId,
            @Parameter(description = "조회할 날짜 (yyyy-MM-dd)", required = true)
            @RequestParam String date) {

        return scheduleService.getSchedulesForFamilyAndDate(familyId, date);
    }

    @Operation(summary = "개인 일정 조회 (특정 날짜)", description = "가족 ID와 사용자 ID, 날짜를 기준으로 개인 일정을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "일정 조회 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = Schedule.class))))
    @PostMapping("/get/{familyId}/{userId}")
    public Optional<List<ScheduleDTO>> getSchedulesForUser(
            @RequestHeader("Authorization") String authorizationHeader,
            @Parameter(description = "가족 ID", required = true)
            @PathVariable UUID familyId,
            @Parameter(description = "사용자 ID", required = true)
            @PathVariable Long userId,
            @Parameter(description = "조회할 날짜 (yyyy-MM-dd)", required = true)
            @RequestParam String date) {

        return scheduleService.getSchedulesForUserAndDate(familyId, userId, date);
    }

    @Operation(summary = "일정 추가", description = "새로운 일정을 추가하고 생성된 일정 ID를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "일정 추가 성공",
            content = @Content(schema = @Schema(implementation = UUID.class)))
    @PostMapping("/add")
    public UUID addSchedule(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody ScheduleDTO scheduleDTO) {

        return scheduleService.addSchedule(scheduleDTO);
    }

    @Operation(summary = "일정 삭제", description = "일정 ID를 기준으로 해당 일정을 삭제합니다.")
    @ApiResponse(responseCode = "200", description = "일정 삭제 성공")
    @DeleteMapping("/remove/{scheduleId}")
    public void removeSchedule(
            @Parameter(description = "삭제할 일정 ID", required = true)
            @PathVariable UUID scheduleId) {

        scheduleService.removeSchedule(scheduleId);
    }

    @Operation(summary = "일정 수정", description = "기존 일정을 수정하고 해당 일정 ID를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "일정 수정 성공",
            content = @Content(schema = @Schema(implementation = UUID.class)))
    @PostMapping("/modify")
    public UUID modifySchedule(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody ScheduleDTO scheduleDTO) {

        return scheduleService.modifySchedule(scheduleDTO);
    }
}
