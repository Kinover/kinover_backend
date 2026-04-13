package com.example.kinover_backend.controller;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.CreateBlockRequest;
import com.example.kinover_backend.service.UserBlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "차단", description = "유저 간 차단 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/blocks")
public class BlockController {

    private final UserBlockService userBlockService;
    private final JwtUtil jwtUtil;

    private Long userIdFromAuth(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            return null;
        }
        return jwtUtil.getUserIdFromToken(token);
    }

    @Operation(
            summary = "내가 차단한 유저 ID 목록",
            description = "Authorization: Bearer 필수(401). 응답 본문은 Long 배열 JSON만 사용합니다(예: [1,2,3]). "
                    + "DB의 차단 테이블이 진실이며, POST /api/blocks·DELETE /api/blocks/{id}·신고 후 자동 차단이 커밋된 직후 동일 트랜잭션 경로로 조회되므로 지연은 DB 커밋·레플리카 지연 수준입니다.")
    @GetMapping
    public ResponseEntity<List<Long>> listBlocked(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = userIdFromAuth(authorizationHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userBlockService.listBlockedUserIds(userId));
    }

    @Operation(summary = "유저 차단")
    @PostMapping
    public ResponseEntity<Void> block(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody CreateBlockRequest body
    ) {
        Long userId = userIdFromAuth(authorizationHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (body == null || body.getBlockedUserId() == null) {
            throw new BadRequestException("blockedUserId는 필수입니다.");
        }
        userBlockService.blockUser(userId, body.getBlockedUserId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "유저 차단 해제")
    @DeleteMapping("/{blockedUserId}")
    public ResponseEntity<Void> unblock(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long blockedUserId
    ) {
        Long userId = userIdFromAuth(authorizationHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        userBlockService.unblockUser(userId, blockedUserId);
        return ResponseEntity.ok().build();
    }
}
