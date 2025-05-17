package com.example.kinover_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class UserStatusDTO {
    private Long userId;
    private boolean isOnline;
    private LocalDateTime lastActiveAt;
}