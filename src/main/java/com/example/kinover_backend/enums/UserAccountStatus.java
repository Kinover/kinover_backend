package com.example.kinover_backend.enums;

public enum UserAccountStatus {
    NORMAL,
    BANNED,
    /** 중복 전화번호 가입 시도 등으로 소셜 연동 해제·가입 무효화된 계정 */
    INVALIDATED
}
