package com.example.kinover_backend.dto;

import lombok.Getter;

@Getter
public class PhoneVerifyRequestDto {

    private String idToken;

    // App Store 심사용 테스트 번호 bypass 필드
    private String testPhone;
    private String testCode;
}
