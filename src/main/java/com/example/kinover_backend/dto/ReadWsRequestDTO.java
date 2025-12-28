// src/main/java/com/example/kinover_backend/dto/ReadWsRequestDTO.java
package com.example.kinover_backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReadWsRequestDTO {
    private String type; // "room:read"
    private UUID chatRoomId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime lastReadAt;
}
