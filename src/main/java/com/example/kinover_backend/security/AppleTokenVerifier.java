package com.example.kinover_backend.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

@Component
public class AppleTokenVerifier {

    private final AppleJwksClient jwksClient;

    @Value("${apple.issuer}")
    private String issuer;

    @Value("${apple.audiences}")
    private List<String> audiences;

    public AppleTokenVerifier(AppleJwksClient jwksClient) {
        this.jwksClient = jwksClient;
    }

    public AppleUserClaims verify(String identityToken) {
        try {
            SignedJWT jwt = SignedJWT.parse(identityToken);
            JWSHeader header = jwt.getHeader();

            String kid = header.getKeyID();
            if (kid == null || kid.isBlank()) {
                throw new IllegalArgumentException("Apple token kid missing");
            }

            RSAPublicKey publicKey = jwksClient.getPublicKeyByKid(kid);
            if (publicKey == null) {
                throw new IllegalArgumentException("Apple public key not found for kid=" + kid);
            }

            boolean verified = jwt.verify(new RSASSAVerifier(publicKey));
            if (!verified) {
                throw new IllegalArgumentException("Apple token signature invalid");
            }

            // iss 검증
            String tokenIss = jwt.getJWTClaimsSet().getIssuer();
            if (!issuer.equals(tokenIss)) {
                throw new IllegalArgumentException("Apple token issuer invalid: " + tokenIss);
            }

            // aud 검증
            List<String> tokenAud = jwt.getJWTClaimsSet().getAudience();
            boolean audOk = tokenAud != null && tokenAud.stream().anyMatch(audiences::contains);
            if (!audOk) {
                throw new IllegalArgumentException("Apple token audience invalid: " + tokenAud);
            }

            // exp 검증
            Date exp = jwt.getJWTClaimsSet().getExpirationTime();
            if (exp == null || exp.before(new Date())) {
                throw new IllegalArgumentException("Apple token expired");
            }

            String sub = jwt.getJWTClaimsSet().getSubject();
            String email = jwt.getJWTClaimsSet().getStringClaim("email"); // 없을 수 있음

            return new AppleUserClaims(sub, email, tokenIss, tokenAud != null && !tokenAud.isEmpty() ? tokenAud.get(0) : null);

        } catch (ParseException e) {
            throw new IllegalArgumentException("Apple token parse failed", e);
        } catch (JOSEException e) {
            throw new IllegalArgumentException("Apple token verify failed", e);
        }
    }
}
