package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.entity.Challenge;
import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.entity.RecChallenge;
import com.example.kinover_backend.service.ChallengeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "챌린지 Controller", description = "챌린지 관련 API를 제공합니다.")
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/challenge")
public class ChallengeController {

    private final ChallengeService  challengeService;

    public ChallengeController(ChallengeService challengeService){
        this.challengeService=challengeService;
    }

    // 특정 가족의 모든 챌린지 제공
    @Operation(summary="챌린지 수신",description = "가족의 모든 챌린지 기록을 가져옵니다.")
    @PostMapping("/all/{familyId}")
    public List<Challenge> getChallengesByFamilyId(@PathVariable UUID familyId) {
        return challengeService.getChallengesByFamilyId(familyId);
    }

    // 특정 가족의 현재 챌린지 제공
    @Operation(summary="챌린지 수신",description = "가족의 모든 챌린지 기록을 가져옵니다.")
    @PostMapping("/current/{familyId}")
    public Challenge getChallengeByChallengeId(@PathVariable UUID familyId) {
        return challengeService.getChallengeByFamilyId(familyId);
    }

    // 특정 챌린지 삭제
    @Operation(summary="챌린지 수신",description = "가족의 모든 챌린지 기록을 가져옵니다.")
    @PostMapping("/delete/{challengeId}")
    public void deleteChallenge(@PathVariable UUID challengeId) {
        challengeService.deleteChallenge(challengeId);
    }

    // 챌린지 저장
    @Operation(summary="챌린지 수신",description = "가족의 모든 챌린지 기록을 가져옵니다.")
    @PostMapping("/save")
    public Challenge saveChallengesByFamilyId(@RequestBody Family family,
    @RequestBody RecChallenge recChallenge) {
        return challengeService.saveChallengeByFamilyId(family,recChallenge);
    }
}
