package com.example.kinover_backend.service;

import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.JwtUtil;
import org.springframework.stereotype.Service;


@Service
public class TokenService {

    private final JwtUtil jwtUtil; // 너희 프로젝트에 있는 JWT 유틸로 교체

    public TokenService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public String issueJwt(User user) {
        return jwtUtil.generateToken(user.getUserId());
    }
}
