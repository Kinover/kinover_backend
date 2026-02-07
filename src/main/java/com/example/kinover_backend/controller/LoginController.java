package com.example.kinover_backend.controller;

import com.example.kinover_backend.dto.AppleLoginDTO;
import com.example.kinover_backend.dto.KakaoUserDto;
import com.example.kinover_backend.dto.LoginResponseDto;
import com.example.kinover_backend.service.AppleUserService;
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
    private final AppleUserService appleUserService;

    public LoginController(KakaoUserService kakaoUserService, AppleUserService appleUserService) {
        this.kakaoUserService = kakaoUserService;
        this.appleUserService = appleUserService;
    }

    @PostMapping("/kakao")
    public ResponseEntity<?> kakaoLogin(@RequestBody KakaoUserDto kakaoUserDto) {
        if (kakaoUserDto == null || kakaoUserDto.getAccessToken() == null || kakaoUserDto.getAccessToken().isBlank()) {
            return ResponseEntity.badRequest().body("Access token is required.");
        }

        System.out.println("카카오 로그인 요청 들어옴: " + kakaoUserDto.getAccessToken());
        LoginResponseDto response = kakaoUserService.processKakaoLogin(kakaoUserDto.getAccessToken());
        return ResponseEntity.ok(response);
    }
    @PostMapping("/apple")
    public ResponseEntity<?> appleLogin(@RequestBody AppleLoginDTO dto) {
        if (dto == null || dto.getIdentityToken() == null || dto.getIdentityToken().isBlank()) {
            return ResponseEntity.badRequest().body("identityToken is required.");
        }
    
        LoginResponseDto response = appleUserService.processAppleLogin(dto.getIdentityToken());
        return ResponseEntity.ok(response);
    }
    
}
