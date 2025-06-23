package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.ScheduleDTO;
import com.example.kinover_backend.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "일정 API", description = "일정 관련 API를 제공합니다.")
@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(summary = "일정 조회", description = "일정을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "일정 목록 반환 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScheduleDTO.class))))
    @PostMapping("/get")
        public ResponseEntity<List<ScheduleDTO>> getSchedules(@RequestBody ScheduleDTO requestDTO) {
        List<ScheduleDTO> schedules = scheduleService.getSchedulesByFilter(requestDTO);
        return ResponseEntity.ok(schedules);
        }

    @Operation(summary = "일정 추가", description = "새로운 일정을 추가합니다.")
    @ApiResponse(responseCode = "200", description = "일정 생성 성공",
            content = @Content(schema = @Schema(implementation = UUID.class)))
    @PostMapping("/add")
    public UUID addSchedule(
            @RequestBody ScheduleDTO scheduleDTO) {
                System.out.println(">>> [ScheduleDTO 수신]: " + scheduleDTO.toString()); // 또는 log.info()

        return scheduleService.addSchedule(scheduleDTO);
    }

    @Operation(summary = "일정 수정", description = "기존 일정을 수정합니다.")
    @ApiResponse(responseCode = "200", description = "일정 수정 성공",
            content = @Content(schema = @Schema(implementation = UUID.class)))
    @PutMapping("/modify")
    public UUID modifySchedule(
            @RequestBody ScheduleDTO scheduleDTO) {

        return scheduleService.modifySchedule(scheduleDTO);
    }

    @Operation(summary = "일정 삭제", description = "일정 ID로 해당 일정을 삭제합니다.")
    @ApiResponse(responseCode = "200", description = "일정 삭제 성공")
    @DeleteMapping("/remove/{scheduleId}")
    public void removeSchedule(
            @Parameter(description = "삭제할 일정 ID", required = true)
            @PathVariable UUID scheduleId) {

        scheduleService.removeSchedule(scheduleId);
    }

    @Operation(
        summary = "특정 월의 날짜별 일정 개수 조회",
        description = "가족 ID와 연도, 월을 기준으로 해당 월의 날짜별 일정 개수를 반환합니다. userId를 전달하면 해당 사용자 일정만 집계합니다.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "날짜별 일정 개수 조회 성공",
                content = @Content(schema = @Schema(implementation = Map.class))
            )
        }
    )
    @GetMapping("/countByDate")
    public ResponseEntity<Map<Integer, Integer>> getScheduleCountByDate(
            @Parameter(description = "가족 ID", required = true)
            @RequestParam UUID familyId,

            @Parameter(description = "연도 (예: 2025)", required = true)
            @RequestParam int year,

            @Parameter(description = "월 (1~12)", required = true)
            @RequestParam int month,

            @Parameter(description = "사용자 ID (선택)", required = false)
            @RequestParam(required = false) Long userId
    ) {
        Map<Integer, Integer> result = scheduleService.getScheduleCountByDate(familyId, year, month, userId);
        return ResponseEntity.ok(result);
    }

}
