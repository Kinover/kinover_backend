package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.Memory;
import lombok.Getter;

import java.util.Date;
import java.util.UUID;

@Getter
public class MemoryDTO {
    private UUID memoryId;
    private String content;
    private Date date;
    private String image;
    private Date createdAt;
    private FamilyDTO family;  // Family 엔티티의 DTO
    private UserDTO user;      // User 엔티티의 DTO

    // Memory 엔티티를 MemoryDTO로 변환하는 생성자
    public MemoryDTO(Memory memory) {
        if(memory.getMemoryId()==null){
            memory.setMemoryId(UUID.randomUUID());
        }
        this.memoryId = memory.getMemoryId();
        this.content = memory.getContent();
        this.date = memory.getDate();
        this.image = memory.getImage();
        this.createdAt = memory.getCreatedAt();
        this.family = new FamilyDTO(memory.getFamily());  // FamilyDTO로 변환
        this.user = new UserDTO(memory.getUser());        // UserDTO로 변환
    }
}
