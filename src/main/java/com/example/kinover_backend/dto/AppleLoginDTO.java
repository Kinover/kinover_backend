package com.example.kinover_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppleLoginDTO {

    @Schema(description = "Apple Sign In identity token (JWT)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String identityToken;

    /**
     * 애플 최초 인증 시에만 클라이언트가 받을 수 있는 이메일. 재로그인 시에는 보통 null.
     * 토큰 내 이메일과 함께 최종 저장 값으로 병합됩니다.
     */
    @Schema(description = "애플이 최초에 전달한 이메일(재로그인 시 null 가능)")
    private String email;

    @Schema(description = "이름(성). Apple PersonNameComponents.familyName")
    private String familyName;

    @Schema(description = "이름(이름). Apple PersonNameComponents.givenName")
    private String givenName;

    /**
     * 선택. 있으면 신규 가입 시에만 저장됩니다. (YYYY-MM-DD)
     */
    @Schema(description = "생년월일, 선택(YYYY-MM-DD). 없어도 가입 가능")
    private String birth;
}
