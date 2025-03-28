package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.KakaoUserDto;
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

    // 프론트에서 로그인 후 카카오 사용자 정보를 백엔드로 전송
    @PostMapping("/kakao")
    public ResponseEntity<String> kakaoLogin(@RequestBody KakaoUserDto kakaoUserDto) {
        System.out.println("카카오 로그인 요청 들어옴 ");
        String jwtToken = kakaoUserService.processKakaoUser(kakaoUserDto);
        return ResponseEntity.ok(jwtToken);  // JWT 토큰을 응답 본문으로 반환
    }

}
