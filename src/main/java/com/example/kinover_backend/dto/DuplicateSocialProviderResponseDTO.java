package com.example.kinover_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DuplicateSocialProviderResponseDTO {
    private String code;
    private String provider;
}
