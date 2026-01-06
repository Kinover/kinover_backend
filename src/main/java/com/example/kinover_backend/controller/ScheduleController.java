// src/main/java/com/example/kinover_backend/controller/ScheduleController.java
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "일정 API", description = "일정 관련 API를 제공합니다.")
@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(
        summary = "일정 조회",
        description = """
            일정을 조회합니다.
            - familyId, date는 필수
            - (선택) userId가 있으면:
              * FAMILY/ANNIVERSARY는 모두 노출
              * INDIVIDUAL은 participants에 userId가 포함된 일정만 노출
              
            ⚠️ 참고:
            - 이 userId는 '조회 필터' 용도이며, 배열(List)을 받지 않습니다.
            - '한 일정에 여러 유저 포함'은 add/modify의 participantIds(List<Long>)로 처리합니다.
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "일정 목록 반환 성공",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScheduleDTO.class)))
    )
    @PostMapping("/get")
    public ResponseEntity<List<ScheduleDTO>> getSchedules(@RequestBody ScheduleDTO requestDTO) {
        List<ScheduleDTO> schedules = scheduleService.getSchedulesByFilter(requestDTO);
        return ResponseEntity.ok(schedules);
    }

    @Operation(
        summary = "일정 추가",
        description = """
            새로운 일정을 추가합니다.
            ✅ 한 일정에 여러 유저(참여자) 포함 가능:
            - participantIds: [1,2,3] 처럼 배열로 전달
            - type이 ANNIVERSARY면 participantIds는 비워야 합니다.
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "일정 생성 성공",
        content = @Content(schema = @Schema(implementation = UUID.class))
    )
    @PostMapping("/add")
    public UUID addSchedule(@RequestBody ScheduleDTO scheduleDTO) {
        System.out.println(">>> [ScheduleDTO 수신]: " + scheduleDTO);
        return scheduleService.addSchedule(scheduleDTO);
    }

    @Operation(
        summary = "일정 수정",
        description = """
            기존 일정을 수정합니다.
            ✅ participantIds를 배열로 보내면 참여자(다중)가 갱신됩니다.
            - type이 ANNIVERSARY면 participantIds는 비워야 합니다.
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "일정 수정 성공",
        content = @Content(schema = @Schema(implementation = UUID.class))
    )
    @PutMapping("/modify")
    public UUID modifySchedule(@RequestBody ScheduleDTO scheduleDTO) {
        return scheduleService.modifySchedule(scheduleDTO);
    }

    @Operation(summary = "일정 삭제", description = "일정 ID로 해당 일정을 삭제합니다.")
    @ApiResponse(responseCode = "200", description = "일정 삭제 성공")
    @DeleteMapping("/remove/{scheduleId}")
    public void removeSchedule(
        @Parameter(description = "삭제할 일정 ID", required = true)
        @PathVariable UUID scheduleId
    ) {
        scheduleService.removeSchedule(scheduleId);
    }

    @Operation(
        summary = "Get monthly schedule count",
        description = "가족 ID와 연도, 월을 기반으로 해당 월의 각 날짜별 일정 개수(타입별 포함)를 반환합니다."
    )
    @GetMapping("/count-per-day")
    public ResponseEntity<Map<LocalDate, Map<String, Long>>> getScheduleCountPerDay(
        @RequestParam UUID familyId,
        @RequestParam int year,
        @RequestParam int month
    ) {
        Map<LocalDate, Map<String, Long>> countPerDay =
            scheduleService.getScheduleCountPerDay(familyId, year, month);
        return ResponseEntity.ok(countPerDay);
    }
}
