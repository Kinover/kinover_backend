package com.example.kinover_backend.entity;

public class TokenRequest {
    private String token; // 요청 바디에서 "token" 값 받음

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
