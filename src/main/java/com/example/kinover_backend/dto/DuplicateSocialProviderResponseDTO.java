package com.example.kinover_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DuplicateSocialProviderResponseDTO {
    private String code;
    /** 기존 계정의 소셜 제공자(KAKAO/APPLE 등). 없으면 null */
    private String provider;
    /** 클라이언트 토스트용 설명(예: 중복 전화번호). 없으면 null */
    private String message;
}
