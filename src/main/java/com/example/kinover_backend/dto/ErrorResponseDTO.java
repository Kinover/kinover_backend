// src/main/java/com/example/kinover_backend/dto/ErrorResponseDTO.java
package com.example.kinover_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponseDTO {
    private String code;
    private String message;
}
