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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

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

        // ✅ 로그인 요청은 패스
        if ("/api/login/kakao".equals(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ 토큰 추출
        String token = extractToken(request);

        // ✅ Authorization 헤더가 있는데 토큰이 비어있으면(예: "Bearer " or "Bearer null") -> 401
        if (token != null) {
            token = token.trim();

            if (token.isEmpty() || "null".equalsIgnoreCase(token) || "undefined".equalsIgnoreCase(token)) {
                sendUnauthorized(response, "TOKEN_MISSING");
                return;
            }

            // ✅ 토큰이 있는데 유효하지 않으면(만료/위조/파싱불가) -> 401
            if (!jwtUtil.isTokenValid(token)) {
                sendUnauthorized(response, "TOKEN_EXPIRED");
                return;
            }

            // ✅ 유효한 토큰이면 인증 세팅
            Long userId = jwtUtil.getUserIdFromToken(token);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // ✅ 토큰이 아예 없으면 여기서는 그냥 통과 (최종적으로 /api/**는 SecurityConfig가 막음)
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
