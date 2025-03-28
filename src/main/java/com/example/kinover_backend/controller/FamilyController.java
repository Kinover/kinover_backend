package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.FamilyDTO;
import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.service.FamilyService;
import com.example.kinover_backend.JwtUtil;  // JwtUtil 임포트 추가
import com.example.kinover_backend.service.UserFamilyService;
import com.example.kinover_backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@Tag(name = "가족 Controller", description = "가족 관련 API를 제공합니다.")
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/family")
public class FamilyController {

    private final FamilyService familyService;
    private final JwtUtil jwtUtil;  // JwtUtil 추가
    private final UserService userService;
    private final UserFamilyService userFamilyService;

    @Autowired
    public FamilyController(FamilyService familyService, JwtUtil jwtUtil, UserService userService, UserFamilyService userFamilyService) {
        this.familyService = familyService;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.userFamilyService = userFamilyService;
    }

    // 가족 정보 조회 (familyId는 PathVariable로 받고, 토큰은 Authorization 헤더에서 받음)
    @Operation(summary = "가족 아이디로 가족 조회", description = "특정 가족의 정보를 조회합니다.")
    @PostMapping("/{familyId}")
    public FamilyDTO getFamily(
            @Parameter(description = "가족 아이디", required = true) @PathVariable UUID familyId,
            @RequestHeader("Authorization") String authorizationHeader) { // 헤더에서 Authorization 받음

        return familyService.getFamilyById(familyId);
    }

    // 가족 추가 (가족 정보와 토큰을 Authorization 헤더에서 받음)
    @Operation(summary = "가족 추가", description = "새로운 가족을 추가합니다.")
    @PostMapping("/add")
    public void addFamily(
            @RequestBody Family family,
            @RequestHeader("Authorization") String authorizationHeader) { // 헤더에서 Authorization 받음
        familyService.addFamily(family);
    }

    // 가족 삭제 (familyId는 PathVariable로 받고, 토큰은 Authorization 헤더에서 받음)
    @Operation(summary = "가족 삭제", description = "특정 가족을 삭제합니다.")
    @PostMapping("/delete/{familyId}")
    public void deleteFamily(
            @Parameter(description = "삭제할 가족의 아이디", required = true) @PathVariable UUID familyId,
            @RequestHeader("Authorization") String authorizationHeader) { // 헤더에서 Authorization 받음

        familyService.deleteFamily(familyId);
    }

    // 가족 정보 수정
    @Operation(summary = "가족 정보 수정", description = "가족 정보를 수정합니다.")
    @PostMapping("/modify")
    public FamilyDTO modifyFamily(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody Family family
    ){
        return familyService.modifyFamily(family);
    }

    // 가족 구성원 추가
    @Operation(summary="가족 구성원 추가",description = "가족 구성원을 추가합니다.")
    @PostMapping("/add/{familyId}/{userId}")
    public void addFamilyUser (@PathVariable UUID familyId, @PathVariable Long userId) {
//        userFamilyService.deleteUserByFamilyIdAndUserId(familyId,userId);
    }

    // 가족 구성원 삭제 (탈퇴)
    @Operation(summary="가족 구성원 탈퇴",description = "가족에서 탈퇴합니다.")
    @PostMapping("/delete-user/{familyId}/{userId}")
    public void deleteFamilyUser (@PathVariable UUID familyId, @PathVariable Long userId) {
        userFamilyService.deleteUserByFamilyIdAndUserId(familyId,userId);
    }


}
