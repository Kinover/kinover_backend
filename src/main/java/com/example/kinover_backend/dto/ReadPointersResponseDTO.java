// src/main/java/com/example/kinover_backend/dto/ReadPointersResponseDTO.java
package com.example.kinover_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ReadPointersResponseDTO {

    private UUID chatRoomId;
    private List<Pointer> pointers;

    @Getter
    @AllArgsConstructor
    public static class Pointer {
        private Long userId;
        private LocalDateTime lastReadAt;
    }
}
