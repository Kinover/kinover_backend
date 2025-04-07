package com.example.kinover_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KakaoUserDto {
    private String accessToken; // RN 요청에서 받을 필드

    @JsonProperty("id")
    private Long kakaoId;

    @JsonProperty("properties")
    private Properties properties;

    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;

    private Integer version;

    @Getter
    @Setter
    public static class Properties {
        private String nickname;
        @JsonProperty("profile_image")
        private String profileImageUrl;
    }

    @Getter
    @Setter
    public static class KakaoAccount {
        private String email;

        @JsonProperty("profile")
        private Profile profile;

        @Getter
        @Setter
        public static class Profile {
            private String nickname;
            @JsonProperty("profile_image_url")
            private String profileImageUrl;
        }
    }

    public String getNickname() {
        return properties != null ? properties.getNickname() :
                (kakaoAccount != null && kakaoAccount.getProfile() != null ? kakaoAccount.getProfile().getNickname() : null);
    }

    public String getProfileImageUrl() {
        return properties != null ? properties.getProfileImageUrl() :
                (kakaoAccount != null && kakaoAccount.getProfile() != null ? kakaoAccount.getProfile().getProfileImageUrl() : null);
    }

    public String getEmail() {
        return kakaoAccount != null ? kakaoAccount.getEmail() : null;
    }
}