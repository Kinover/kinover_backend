package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.MemoryDTO;
import com.example.kinover_backend.entity.Memory;
import com.example.kinover_backend.service.MemoryService;
import com.example.kinover_backend.JwtUtil;  // JwtUtil 임포트 추가
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Tag(name = "추억 Controller", description = "추억 관련 API를 제공합니다.")
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryService memoryService;
    private final JwtUtil jwtUtil;  // JwtUtil 추가

    @Autowired
    public MemoryController(MemoryService memoryService, JwtUtil jwtUtil) {
        this.memoryService = memoryService;
        this.jwtUtil = jwtUtil;
    }

    @Operation(summary = "가족 아이디로 추억 조회", description = "특정 가족의 추억을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "추억 목록 조회 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = Memory.class))))
    @PostMapping("/{familyId}")
    public Optional<List<MemoryDTO>> getMemoryByFamilyId(
            @Parameter(description = "가족 아이디", required = true)
            @PathVariable UUID familyId,
            @RequestHeader("Authorization") String authorizationHeader) {  // 헤더에서 Authorization 받음

        return memoryService.getMemoryByFamilyId(familyId);
    }

    @Operation(summary = "추억 추가", description = "새로운 추억을 추가합니다.")
    @ApiResponse(responseCode = "200", description = "추억 추가 성공")
    @PostMapping("/add")
    public void addMemory(
            @RequestBody Memory memory,
            @RequestHeader("Authorization") String authorizationHeader) {  // 헤더에서 Authorization 받음


        memoryService.addMemory(memory);
    }

    @Operation(summary = "추억 삭제", description = "특정 추억을 삭제합니다.")
    @ApiResponse(responseCode = "200", description = "추억 삭제 성공")
    @PostMapping("/delete/{memoryId}")
    public void removeMemory(
            @Parameter(description = "삭제할 추억의 아이디", required = true)
            @PathVariable UUID memoryId,
            @RequestHeader("Authorization") String authorizationHeader) {  // 헤더에서 Authorization 받음


        memoryService.deleteMemoryByMemoryId(memoryId);
    }
}
