package com.example.kinover_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {
    private String name;

    @Schema(description = "생년월일 (YYYY-MM-DD). 선택 사항 — 없어도 프로필 저장 가능")
    private String birth;

    private Boolean termsAgreed;
    private Boolean privacyAgreed;
    private Boolean marketingAgreed;

    private String termsVersion;
    private String privacyVersion;

    private String agreedAt;           // ISO date string or "YYYY-MM-DD"
    private String marketingAgreedAt;  // optional
}

