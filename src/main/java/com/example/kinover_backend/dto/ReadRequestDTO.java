// src/main/java/com/example/kinover_backend/dto/ReadRequestDTO.java
package com.example.kinover_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ReadRequestDTO {
    private LocalDateTime lastReadAt;
}
