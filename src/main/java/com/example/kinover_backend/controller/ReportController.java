package com.example.kinover_backend.controller;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.dto.CreateReportRequest;
import com.example.kinover_backend.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "신고", description = "UGC 신고 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
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

    @Operation(summary = "콘텐츠/유저 신고")
    @PostMapping
    public ResponseEntity<Void> report(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody CreateReportRequest body
    ) {
        Long userId = userIdFromAuth(authorizationHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        reportService.createReport(userId, body);
        return ResponseEntity.ok().build();
    }
}
