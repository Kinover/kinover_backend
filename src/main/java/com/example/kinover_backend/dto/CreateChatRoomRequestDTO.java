// src/main/java/com/example/kinover_backend/dto/CreateChatRoomRequestDTO.java
package com.example.kinover_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreateChatRoomRequestDTO {
    private UUID familyId;
    private String roomName;
    private List<Long> userIds;
}
