package com.example.kinover_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class PhoneVerifyRequestDto {

    @NotNull(message = "userId는 필수입니다.")
    private Long userId;

    @NotBlank(message = "Firebase idToken은 필수입니다.")
    private String idToken;
}
