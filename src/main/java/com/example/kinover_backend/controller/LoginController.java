package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.KakaoTokenRequest;
import com.example.kinover_backend.service.KakaoUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "로그인 Controller", description = "사용자 로그인 및 인증 관리")
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/login")
public class LoginController {

    private final KakaoUserService kakaoUserService;

    public LoginController(KakaoUserService kakaoUserService) {
        this.kakaoUserService = kakaoUserService;
    }

    @PostMapping("/kakao")
    public ResponseEntity<String> kakaoLogin(@RequestBody KakaoTokenRequest tokenRequest) {
        if (tokenRequest == null || tokenRequest.getAccessToken() == null) {
            System.out.println("액세스 토큰이 제공되지 않음");
            return ResponseEntity.badRequest().body("Access token is required");
        }

        try {
            System.out.println("카카오 로그인 요청 들어옴: " + tokenRequest.getAccessToken());
            String jwtToken = kakaoUserService.processKakaoLogin(tokenRequest.getAccessToken());
            return ResponseEntity.ok(jwtToken);
        } catch (RuntimeException e) {
            System.out.println("카카오 로그인 실패: " + e.getMessage());
            return ResponseEntity.status(500).body("Login failed: " + e.getMessage());
        }
    }
}