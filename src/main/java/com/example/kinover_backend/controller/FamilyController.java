package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.FamilyDTO;
import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.service.FamilyService;
import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.service.UserFamilyService;
import com.example.kinover_backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "가족 Controller", description = "가족 관련 API를 제공합니다.")
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/family")
public class FamilyController {

    private final FamilyService familyService;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final UserFamilyService userFamilyService;

    @Autowired
    public FamilyController(FamilyService familyService, JwtUtil jwtUtil, UserService userService, UserFamilyService userFamilyService) {
        this.familyService = familyService;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.userFamilyService = userFamilyService;
    }

    @Operation(summary = "가족 아이디로 가족 조회", description = "특정 가족의 정보를 조회합니다.")
    @PostMapping("/{familyId}")
    public FamilyDTO getFamily(
            @Parameter(description = "가족 아이디", required = true) @PathVariable UUID familyId,
            @RequestHeader("Authorization") String authorizationHeader) {
        return familyService.getFamilyById(familyId);
    }

    @Operation(summary = "가족 추가", description = "새로운 가족을 추가합니다.")
    @PostMapping("/add")
    public FamilyDTO addFamily(
            @RequestBody Family family,
            @RequestHeader("Authorization") String authorizationHeader) {
        return familyService.addFamily(family);
    }

    @Operation(summary = "가족 삭제", description = "특정 가족을 삭제합니다.")
    @PostMapping("/delete/{familyId}")
    public void deleteFamily(
            @Parameter(description = "삭제할 가족의 아이디", required = true) @PathVariable UUID familyId,
            @RequestHeader("Authorization") String authorizationHeader) {
        familyService.deleteFamily(familyId);
    }

    @Operation(summary = "가족 정보 수정", description = "가족 정보를 수정합니다.")
    @PostMapping("/modify")
    public FamilyDTO modifyFamily(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody Family family) {
        return familyService.modifyFamily(family);
    }

    @Operation(summary = "가족 구성원 추가", description = "가족 구성원을 추가합니다.")
    @PostMapping("/add/{familyId}/{userId}")
    public ResponseEntity<String> addFamilyUser(
            @Parameter(description = "가족 아이디", required = true) @PathVariable UUID familyId,
            @Parameter(description = "추가할 유저 아이디", required = true) @PathVariable Long userId,
            @RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        Long authenticatedUserId = jwtUtil.getUserIdFromToken(token);

        // 요청자가 인증된 유저인지 확인 (선택적, 필요 시 제거 가능)
        // if (!authenticatedUserId.equals(userId)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body("자신만 가족에 추가할 수 있습니다");
        // }

        userFamilyService.addUserByFamilyIdAndUserId(familyId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body("가족 구성원이 성공적으로 추가되었습니다");
    }

    @Operation(summary = "가족 구성원 탈퇴", description = "가족에서 탈퇴합니다.")
    @PostMapping("/delete-user/{familyId}/{userId}")
    public void deleteFamilyUser(
            @Parameter(description = "가족 아이디", required = true) @PathVariable UUID familyId,
            @Parameter(description = "탈퇴할 유저 아이디", required = true) @PathVariable Long userId) {
        userFamilyService.deleteUserByFamilyIdAndUserId(familyId, userId);
    }
}