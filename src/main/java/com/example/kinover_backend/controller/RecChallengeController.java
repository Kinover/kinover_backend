package com.example.kinover_backend.controller;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.RecChallengeDTO;
import com.example.kinover_backend.service.RecChallengeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "추천 챌린지 Controller", description = "추천 챌린지 관련 API를 제공합니다.")
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/recChallenge")
public class RecChallengeController {

    private final RecChallengeService recChallengeService;
    private final JwtUtil jwtUtil;

    @Autowired
    public RecChallengeController(RecChallengeService recChallengeService, JwtUtil jwtUtil) {
        this.recChallengeService = recChallengeService;
        this.jwtUtil = jwtUtil;
    }

    @Operation(summary = "추천 챌린지 조회", description = "추천 챌린지를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "추천 챌린지 조회 성공",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = RecChallengeDTO.class))))
    @PostMapping("/get")
    public List<RecChallengeDTO> getRecChallenges(
            @RequestHeader("Authorization") String authorizationHeader) { // 헤더에서 Authorization 받음

        return recChallengeService.getRecChallenges();
    }
}
