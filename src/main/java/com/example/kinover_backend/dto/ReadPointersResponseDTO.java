// src/main/java/com/example/kinover_backend/dto/ReadPointersResponseDTO.java
package com.example.kinover_backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReadPointersResponseDTO {
    private UUID chatRoomId;
    private List<Pointer> pointers;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pointer {
        private Long userId;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
        private LocalDateTime lastReadAt;
    }
}
