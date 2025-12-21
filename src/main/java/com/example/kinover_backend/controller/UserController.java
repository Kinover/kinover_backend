package com.example.kinover_backend.controller;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.NotificationResponseDTO;
import com.example.kinover_backend.dto.UpdateProfileRequest;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.service.UserService;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

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

    @Operation(summary = "토큰으로 유저 조회", description = "JWT 토큰을 이용해 유저 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "유저 정보 조회 성공", content = @Content(schema = @Schema(implementation = UserDTO.class)))
    @GetMapping("/userinfo")
    public UserDTO getUserInfo(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        Claims claims = jwtUtil.parseToken(token);
        Long userId = Long.parseLong(claims.getSubject());
        return userService.getUserById(userId);
    }

    @Operation(summary = "회원 탈퇴", description = "JWT 토큰 기반으로 본인만 탈퇴할 수 있습니다. 탈퇴 후 익명화 처리됩니다.")
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteUser(@RequestHeader("Authorization") String authorizationHeader) {
        String jwt = authorizationHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(jwt);

        userService.deleteUserById(userId);
        return ResponseEntity.ok().body("회원 탈퇴가 완료되었습니다.");
    }

    @Operation(summary = "유저 정보 수정", description = "userId를 포함한 DTO를 기반으로 유저 정보를 수정합니다.")
    @ApiResponse(responseCode = "200", description = "수정된 유저 정보 반환", content = @Content(schema = @Schema(implementation = UserDTO.class)))
    @PostMapping("/modify")
    public UserDTO modifyUser(@RequestBody UserDTO userDTO) {
        return userService.modifyUser(userDTO);
    }

    @Operation(summary = "사용자 알림 조회", description = "알림 화면 진입 시 호출. 호출 시점에 읽음 처리됩니다.")
    @GetMapping("/notifications")
    public NotificationResponseDTO getUserNotifications(@RequestHeader("Authorization") String authorizationHeader) {
        String jwt = authorizationHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(jwt);

        return userService.getUserNotifications(userId);
    }

    @Operation(summary = "안읽은 알림 존재 여부", description = "벨 아이콘 빨간점 표시용(hasUnread=true/false)")
    @GetMapping("/notifications/unread")
    public Map<String, Boolean> hasUnreadNotifications(@RequestHeader("Authorization") String authorizationHeader) {
        String jwt = authorizationHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(jwt);

        boolean hasUnread = userService.hasUnreadNotifications(userId);
        return Collections.singletonMap("hasUnread", hasUnread);
    }

    @Operation(summary = "전체 프로필 업데이트", description = "JWT 기반으로 사용자 프로필을 업데이트합니다.")
    @PatchMapping("/profile")
    public ResponseEntity<?> updateUserProfile(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody UpdateProfileRequest request) {
        String jwt = authorizationHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserIdFromToken(jwt);

        userService.updateUserProfile(userId, request);
        return ResponseEntity.ok().body("프로필 업데이트가 완료되었습니다.");
    }
}
