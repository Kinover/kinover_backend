// src/main/java/com/example/kinover_backend/config/JwtAuthenticationFilter.java
package com.example.kinover_backend.config;

import com.example.kinover_backend.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        final String uri = request.getRequestURI();

        // ✅ Preflight 요청은 바로 통과
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ 로그인 요청은 패스
        if ("/api/login/kakao".equals(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);

        // 토큰이 없는 경우: 여기서는 그냥 통과
        // (최종적으로 /api/** 는 SecurityConfig에서 authenticated로 막힘)
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        token = token.trim();

        // ✅ "Bearer "만 오거나 null/undefined면 401
        if (token.isEmpty() || "null".equalsIgnoreCase(token) || "undefined".equalsIgnoreCase(token)) {
            sendUnauthorized(response, "TOKEN_MISSING");
            return;
        }

        // ✅ 토큰 유효성 체크
        if (!jwtUtil.isTokenValid(token)) {
            sendUnauthorized(response, "TOKEN_EXPIRED");
            return;
        }

        // ✅ 유효한 토큰이면 인증 세팅
        Long userId = jwtUtil.getUserIdFromToken(token);

        // ✅ 권한은 최소 1개 넣어두는 게 안전 (403 방지에 도움)
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userId, null, authorities);

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // ✅ 디버그 로그(필요 시)
        // System.out.println(">>> AUTH SET: " + authentication);

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null) return null;

        if (authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return null;
    }

    private void sendUnauthorized(HttpServletResponse response, String code) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(code);
    }
}
