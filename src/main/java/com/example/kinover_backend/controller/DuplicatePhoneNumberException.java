package com.example.kinover_backend.controller;

public class DuplicatePhoneNumberException extends RuntimeException {
    private final String provider; // 기존 계정의 소셜 제공자 (KAKAO / APPLE)

    public DuplicatePhoneNumberException(String provider) {
        super("이미 해당 전화번호로 가입된 계정이 있습니다.");
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}
