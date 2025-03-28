package com.example.kinover_backend.controller;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.service.UserFamilyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "유저-가족 Controller", description = "유저-가족 관련 API를 제공합니다.")
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/userFamily")
public class UserFamilyController {

    private final JwtUtil jwtUtil;
    private final UserFamilyService userFamilyService;

    public UserFamilyController(JwtUtil jwtUtil, UserFamilyService userFamilyService) {
        this.jwtUtil = jwtUtil;
        this.userFamilyService = userFamilyService;
    }

    @Operation(summary = "가족 아이디로 특정 가족에 해당하는 유저 정보 조회", description = "특정 유저 - 특정 가족 정보를 조회합니다.")
    @PostMapping("/familyUsers/{familyId}") // GET에서 POST로 변경
    public List<UserDTO> getUserFamily(
            @RequestHeader("Authorization") String authorizationHeader, // 헤더에서 Authorization 받기
            @Parameter(description = "가족 아이디", required = true)
            @PathVariable UUID familyId) {

        List<UserDTO> userList = userFamilyService.getUsersByFamilyId(familyId);
        return userList;
    }
}
