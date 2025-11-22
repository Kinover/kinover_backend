package com.example.kinover_backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {
    private String name;
    private String birth; // "YYYY-MM-DD"
}

