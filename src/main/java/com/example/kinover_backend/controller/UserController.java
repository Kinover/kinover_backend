package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.NotificationResponseDTO;
import com.example.kinover_backend.dto.UpdateProfileRequest;
import com.example.kinover_backend.dto.UserDTO;
import com.example.kinover_backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "유저 Controller", description = "유저 관련 API를 제공합니다.")
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    private Long getAuthUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new RuntimeException("인증 정보가 없습니다.");
        }
        return (Long) auth.getPrincipal();
    }

    @Operation(summary = "토큰으로 유저 조회", description = "JWT 토큰을 이용해 유저 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "유저 정보 조회 성공",
            content = @Content(schema = @Schema(implementation = UserDTO.class)))
    @GetMapping("/userinfo")
    public UserDTO getUserInfo() {
        Long userId = getAuthUserId();
        return userService.getUserById(userId);
    }

    @Operation(summary = "회원 탈퇴", description = "JWT 토큰 기반으로 본인만 탈퇴할 수 있습니다. 탈퇴 후 익명화 처리됩니다.")
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteUser() {
        Long userId = getAuthUserId();
        userService.deleteUserById(userId);
        return ResponseEntity.ok().body("회원 탈퇴가 완료되었습니다.");
    }

    @Operation(summary = "유저 정보 수정", description = "userId를 포함한 DTO를 기반으로 유저 정보를 수정합니다.")
    @ApiResponse(responseCode = "200", description = "수정된 유저 정보 반환",
            content = @Content(schema = @Schema(implementation = UserDTO.class)))
    @PostMapping("/modify")
    public UserDTO modifyUser(@RequestBody UserDTO userDTO) {
        return userService.modifyUser(userDTO);
    }

    @Operation(summary = "사용자 알림 조회",
            description = "알림 목록 조회만 합니다. 읽음 처리는 /notifications/mark-read로만 합니다.")
    @GetMapping("/notifications")
    public NotificationResponseDTO getUserNotifications() {
        Long userId = getAuthUserId();
        return userService.getUserNotifications(userId);
    }

    @Operation(summary = "안읽은 알림 존재 여부", description = "벨 아이콘 빨간점 표시용(hasUnread=true/false)")
    @GetMapping("/notifications/unread")
    public Map<String, Boolean> hasUnreadNotifications() {
        Long userId = getAuthUserId();
        boolean hasUnread = userService.hasUnreadNotifications(userId);
        return Collections.singletonMap("hasUnread", hasUnread);
    }

    @Operation(summary = "안읽은 알림 개수", description = "벨(종) 뱃지 숫자(unreadCount) 표시용 (채팅 제외)")
    @GetMapping("/notifications/unread-count")
    public Map<String, Long> getUnreadCount() {
        Long userId = getAuthUserId();
        long unreadCount = userService.getUnreadNotificationCount(userId);
        return Collections.singletonMap("unreadCount", unreadCount);
    }

    @Operation(summary = "알림 읽음 처리", description = "lastNotificationCheckedAt을 now로 갱신(읽음 확정)")
    @PostMapping("/notifications/mark-read")
    public Map<String, Object> markRead() {
        Long userId = getAuthUserId();

        LocalDateTime lastCheckedAt = userService.markNotificationsRead(userId);

        Map<String, Object> res = new HashMap<>();
        res.put("lastCheckedAt", lastCheckedAt.toString());
        res.put("hasUnread", false);
        res.put("unreadCount", 0);
        return res;
    }

    @Operation(summary = "채팅 안읽음 합계", description = "채팅 뱃지 표시용 (종과 분리)")
    @GetMapping("/chat/unread-count")
    public Map<String, Long> getChatUnreadCount() {
        Long userId = getAuthUserId();
        long chatUnreadCount = userService.getChatUnreadCount(userId);
        return Collections.singletonMap("chatUnreadCount", chatUnreadCount);
    }

    @Operation(summary = "앱 아이콘 배지 개수", description = "badgeCount = (종 unread + 채팅 unread)")
    @GetMapping("/badge-count")
    public Map<String, Long> getBadgeCount() {
        Long userId = getAuthUserId();
        long badgeCount = userService.getBadgeCount(userId);
        return Collections.singletonMap("badgeCount", badgeCount);
    }

    @Operation(summary = "전체 프로필 업데이트", description = "JWT 기반으로 사용자 프로필을 업데이트합니다.")
    @PatchMapping("/profile")
    public ResponseEntity<?> updateUserProfile(
            @RequestBody UpdateProfileRequest request
    ) {
        Long userId = getAuthUserId();
        userService.updateUserProfile(userId, request);
        return ResponseEntity.ok().body("프로필 업데이트가 완료되었습니다.");
    }
}
