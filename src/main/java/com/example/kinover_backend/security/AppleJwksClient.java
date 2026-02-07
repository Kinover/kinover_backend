package com.example.kinover_backend.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.security.interfaces.RSAPublicKey;
import java.time.Instant;

@Component
public class AppleJwksClient {

    private static final String JWKS_URL = "https://appleid.apple.com/auth/keys";

    private final RestTemplate restTemplate = new RestTemplate();

    // 간단 캐시(자주 안 바뀜)
    private volatile JWKSet cached;
    private volatile Instant cachedAt;

    public RSAPublicKey getPublicKeyByKid(String kid) {
        JWKSet jwkSet = getJwkSet();
        for (JWK jwk : jwkSet.getKeys()) {
            if (kid.equals(jwk.getKeyID()) && jwk instanceof RSAKey rsaKey) {
                try {
                    return rsaKey.toRSAPublicKey();
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private JWKSet getJwkSet() {
        // 1시간 캐시
        if (cached != null && cachedAt != null && cachedAt.plusSeconds(3600).isAfter(Instant.now())) {
            return cached;
        }
        String json = restTemplate.getForObject(JWKS_URL, String.class);
        try {
            cached = JWKSet.parse(json);
            cachedAt = Instant.now();
            return cached;
        } catch (Exception e) {
            throw new IllegalStateException("Apple JWKS parse failed", e);
        }
    }
}
