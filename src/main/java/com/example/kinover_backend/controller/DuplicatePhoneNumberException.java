package com.example.kinover_backend.controller;

public class DuplicatePhoneNumberException extends RuntimeException {
    private final String provider; // 기존 계정의 소셜 제공자 (KAKAO / APPLE)

    public DuplicatePhoneNumberException(String provider) {
        super("이미 가입된 전화번호입니다. 해당 번호로 연결된 계정으로 로그인해 주세요.");
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}
