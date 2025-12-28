// src/main/java/com/example/kinover_backend/dto/ReadWsRequestDTO.java
package com.example.kinover_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class ReadWsRequestDTO {

    // WS payload에 type이 들어오니, DTO가 받아도 되고 안 받아도 됨
    // 안전하게 받아둠
    private String type;

    private UUID chatRoomId;
    private LocalDateTime lastReadAt;

    @JsonProperty("chatRoomId")
    public UUID getChatRoomId() {
        return chatRoomId;
    }
}
