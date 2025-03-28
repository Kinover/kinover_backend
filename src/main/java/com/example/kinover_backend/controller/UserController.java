package com.example.kinover_backend.controller;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.service.UserService;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "유저 Controller", description = "유저 관련 API를 제공합니다.")
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Autowired
    public UserController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    // 토큰 유저 아이디 Long 타입으로 타입 변환하고 아이디 기반으로 정보 조회해서 유저 정보 전송
    @Operation(summary = "토큰으로 유저 조회", description = "JWT 토큰을 이용해 유저 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "유저 정보 조회 성공",
            content = @Content(schema = @Schema(implementation = UserDTO.class)))
    @PostMapping("/userinfo")
    public UserDTO getUserInfo(
            @RequestHeader("Authorization") String authorizationHeader) { // 헤더에서 Authorization 받기

        String token = authorizationHeader.replace("Bearer ", "");  // "Bearer " 접두어 제거

        // 토큰에서 kakaoId 추출
        Claims claims = jwtUtil.parseToken(token);
        Long kakaoId = Long.parseLong(claims.getSubject()); // 토큰의 subject(kakaoId) 가져오기


        // kakaoId로 유저 정보 조회 후 반환
        return userService.getUserById(kakaoId);
    }

    // 회원 탈퇴
    @Operation(summary = "회원 탈퇴",description = "유저 아이디를 통해 회원을 탈퇴합니다.")
    @PostMapping("/delete/{userId}")
    public void deleteUser(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long userId) { // 헤더에서 Authorization 받기
        userService.deleteUser(userId);
    }

    // 프로필 수정
    @Operation(summary="프로필 변경",description = "유저의 프로필을 변경합니다.")
    @PostMapping("/modify")
    public UserDTO modifyUser(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody User user) { // 헤더에서 Authorization 받기
        System.out.println("프로필 수정 요청 수신");
        return userService.modifyUser(user);
    }
}
