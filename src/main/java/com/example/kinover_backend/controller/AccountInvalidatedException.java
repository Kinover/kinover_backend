package com.example.kinover_backend.controller;

public class AccountInvalidatedException extends RuntimeException {

    public AccountInvalidatedException() {
        super("가입이 취소된 계정입니다. 다시 로그인하여 진행해 주세요.");
    }
}
