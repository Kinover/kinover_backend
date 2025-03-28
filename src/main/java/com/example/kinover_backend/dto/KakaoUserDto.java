package com.example.kinover_backend.dto;

import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KakaoUserDto {
    private Long kakaoId;
    private String nickname;
    private String email;
    private String profileImageUrl;
    private Integer version;
}
