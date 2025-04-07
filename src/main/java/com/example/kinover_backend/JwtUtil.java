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
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expirationTime; // application.properties의 3600000 사용

    // 토큰 생성
    public String generateToken(Long kakaoId) {
        if (secretKey == null || secretKey.length() < 16) { // 16바이트 이상 허용
            throw new IllegalArgumentException("JWT secret key must be at least 16 characters long");
        }

        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(kakaoId.toString()) // 유저의 고유 ID (sub) -> Long을 String으로 변환
                .setIssuedAt(Date.from(now)) // 발급 시간
                .setExpiration(Date.from(now.plusSeconds(expirationTime))) // 3600000ms (1시간)
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256) // HS256 명시
                .compact();
    }

    // 토큰 파싱
    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            throw new ExpiredJwtException(e.getHeader(), e.getClaims(), "Token has expired", e);
        } catch (MalformedJwtException e) {
            throw new MalformedJwtException("Invalid token format", e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtException("Invalid token", e);
        }
    }

    // 토큰 검증 및 유효성 체크
    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseToken(token); // 토큰 파싱 시도
            return claims.getExpiration().after(new Date()); // 만료 시간이 현재 시간 이후인지 확인
        } catch (JwtException e) {
            return false; // 유효하지 않거나 만료된 경우
        }
    }

    // 토큰에서 사용자 ID 추출
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return Long.valueOf(claims.getSubject()); // 유저 ID를 Long으로 변환하여 반환
        } catch (ExpiredJwtException e) {
            throw new ExpiredJwtException(e.getHeader(), e.getClaims(), "Token has expired", e);
        } catch (JwtException e) {
            throw new JwtException("Failed to extract user ID from token", e);
        }
    }

    // 토큰을 검증하고 유효하지 않으면 예외를 던짐
    public void validateToken(String token) {
        if (!isTokenValid(token)) {
            Claims claims = parseToken(token); // 예외를 발생시켜 구체적인 원인을 던짐
            throw new JwtException("Invalid or expired token"); // 이 줄은 도달하지 않음, 위에서 예외 발생
        }
    }
}