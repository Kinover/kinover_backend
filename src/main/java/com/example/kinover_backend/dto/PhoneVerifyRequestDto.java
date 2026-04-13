package com.example.kinover_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class PhoneVerifyRequestDto {

    @NotBlank(message = "Firebase idToken은 필수입니다.")
    private String idToken;
}
