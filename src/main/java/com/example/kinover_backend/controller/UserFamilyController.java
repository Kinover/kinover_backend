package com.example.kinover_backend.controller;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.service.UserFamilyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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

    @Operation(
            summary = "가족 아이디로 특정 가족에 해당하는 유저 정보 조회",
            description = "Authorization: Bearer 필수(401). 조회자(viewer)가 차단한 가족 멤버는 응답에서 제외됩니다.")
    @PostMapping("/familyUsers/{familyId}") // GET에서 POST로 변경
    public List<UserDTO> getUserFamily(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Parameter(description = "가족 아이디", required = true)
            @PathVariable UUID familyId) {

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        Long viewerId = jwtUtil.getUserIdFromToken(token);

        return userFamilyService.getUsersByFamilyId(familyId, viewerId);
    }
}
