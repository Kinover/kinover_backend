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

    /**
     * ✅ application.properties 예)
     * jwt.expiration=3600000  (밀리초 기준, 1시간)
     */
    @Value("${jwt.expiration}")
    private long expirationTime;

    // ✅ 토큰 생성 (subject = userId)
    public String generateToken(Long userId) {
        if (secretKey == null || secretKey.length() < 16) {
            throw new IllegalArgumentException("JWT secret key must be at least 16 characters long");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        Instant now = Instant.now();

        // ✅ 핵심 수정: expirationTime이 밀리초(3600000)라면 plusMillis를 써야 함
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusMillis(expirationTime)))
                .signWith(
                        Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256
                )
                .compact();
    }

    // ✅ 토큰 파싱(서명 검증 포함)
    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            // 만료는 호출부에서 구분할 수 있도록 그대로 던짐
            throw e;
        } catch (MalformedJwtException e) {
            throw new MalformedJwtException("Invalid token format", e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtException("Invalid token", e);
        }
    }

    // ✅ 토큰 유효성 체크
    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration() != null && claims.getExpiration().after(new Date());
        } catch (JwtException e) {
            return false;
        }
    }

    // ✅ 토큰에서 userId 추출
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) {
                throw new JwtException("Token subject is empty");
            }
            return Long.valueOf(sub);
        } catch (ExpiredJwtException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtException("Failed to extract user ID from token", e);
        }
    }

    // ✅ 필요 시 강제 검증
    public void validateToken(String token) {
        if (!isTokenValid(token)) {
            throw new JwtException("Invalid or expired token");
        }
    }
}
