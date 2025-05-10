package com.example.kinover_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class LoginResponseDto {
    private String token;
    private boolean hasFamily;
}