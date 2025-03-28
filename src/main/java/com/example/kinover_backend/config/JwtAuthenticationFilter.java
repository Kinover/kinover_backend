package com.example.kinover_backend.config;

import com.example.kinover_backend.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 🔥 로그인 요청이면 필터 실행 안 함
        if (request.getRequestURI().equals("/api/login/kakao")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request); // 요청 헤더에서 JWT 토큰 추출

        if (token != null && jwtUtil.isTokenValid(token)) {
            Long userId = jwtUtil.getUserIdFromToken(token); // 토큰에서 유저 ID 추출 (Long으로 변경)

            // 사용자 아이디만을 사용하여 인증 객체 생성
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());

            System.out.println(authentication);

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response); // 다음 필터로 요청 전달
    }

    private String extractToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7); // "Bearer "를 제외한 토큰 부분만 반환
        }
        return null;
    }
}
