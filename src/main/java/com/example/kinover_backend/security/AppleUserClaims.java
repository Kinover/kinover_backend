package com.example.kinover_backend.security;

public class AppleUserClaims {
    private final String sub;
    private final String email;
    private final String issuer;
    private final String audience;

    public AppleUserClaims(String sub, String email, String issuer, String audience) {
        this.sub = sub;
        this.email = email;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String getSub() { return sub; }
    public String getEmail() { return email; }
    public String getIssuer() { return issuer; }
    public String getAudience() { return audience; }
}
