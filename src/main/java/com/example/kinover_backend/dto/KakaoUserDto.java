package com.example.kinover_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KakaoUserDto {
    private String accessToken;

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
        @JsonProperty("phone_number")
        private String phoneNumber;  // 전화번호 추가
        private String birthyear;    // 생년 추가
        private String birthday;     // 생일 추가 (MMDD 형식)
        private String name;         // 이름 추가

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

    public String getPhoneNumber() {
        return kakaoAccount != null ? kakaoAccount.getPhoneNumber() : null;
    }

    public String getBirthyear() {
        return kakaoAccount != null ? kakaoAccount.getBirthyear() : null;
    }

    public String getBirthday() {
        return kakaoAccount != null ? kakaoAccount.getBirthday() : null;
    }
}