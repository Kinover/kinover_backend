package com.example.kinover_backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {
    private String name;
    private String birth; // "YYYY-MM-DD"

    private Boolean termsAgreed;
    private Boolean privacyAgreed;
    private Boolean marketingAgreed;

    private String termsVersion;
    private String privacyVersion;

    private String agreedAt;           // ISO date string or "YYYY-MM-DD"
    private String marketingAgreedAt;  // optional
}

