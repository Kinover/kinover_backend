package com.example.kinover_backend;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret-key}")
    private String SECRET_KEY;
    private static final long EXPIRATION_TIME = 60 * 60 * 24; // 24시간

    // 토큰 생성
    public String generateToken(Long kakaoId) {
        if (SECRET_KEY == null || SECRET_KEY.length() < 16) {
            throw new RuntimeException("Invalid secret key");
        }

        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(kakaoId.toString()) // 유저의 고유 ID (sub) -> Long을 String으로 변환
                .setIssuedAt(Date.from(now)) // 발급 시간
                .setExpiration(Date.from(now.plusSeconds(EXPIRATION_TIME))) // 만료 시간
                .signWith(Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8))) // 서명
                .compact();
    }

    // 토큰 파싱
    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            throw new RuntimeException("Invalid or expired token", e);
        }
    }

    // 토큰 검증 및 유효성 체크
    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseToken(token); // 토큰 파싱 시도
            Date expiration = claims.getExpiration();
            return expiration.after(new Date()); // 만료 시간이 현재 시간 이후인 경우 유효한 토큰
        } catch (RuntimeException e) {
            return false; // 토큰이 유효하지 않거나 만료되었을 경우
        }
    }

    // 토큰에서 사용자 ID 추출 (예: 유저 인증에 사용)
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return Long.valueOf(claims.getSubject());  // 유저 ID를 Long으로 변환하여 반환
        } catch (RuntimeException e) {
            throw new RuntimeException("Invalid or expired token", e);
        }
    }

    // 토큰을 검증하고 유효하지 않으면 예외를 던짐
    public void validateToken(String token) {
        if (!isTokenValid(token)) {
            throw new RuntimeException("Invalid or expired token");
        }
    }
}
