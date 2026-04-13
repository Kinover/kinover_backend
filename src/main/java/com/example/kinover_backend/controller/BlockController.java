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
