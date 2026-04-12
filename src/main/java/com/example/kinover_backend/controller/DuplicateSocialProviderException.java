package com.example.kinover_backend.controller;

public class DuplicateSocialProviderException extends RuntimeException {
    private final String provider;

    public DuplicateSocialProviderException(String provider) {
        super("이미 다른 소셜 제공자로 가입된 계정입니다.");
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}
