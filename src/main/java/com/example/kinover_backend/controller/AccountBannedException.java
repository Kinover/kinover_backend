package com.example.kinover_backend.controller;

public class AccountBannedException extends RuntimeException {

    public AccountBannedException() {
        super("계정이 제재되어 이용할 수 없습니다.");
    }
}
