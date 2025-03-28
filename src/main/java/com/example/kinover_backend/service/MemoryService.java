package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.MemoryDTO;
import com.example.kinover_backend.entity.Memory;
import com.example.kinover_backend.repository.MemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MemoryService {

    private final MemoryRepository memoryRepository;

    @Autowired
    public MemoryService(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    // 가족 아이디로 추억 조회 (MemoryDTO 반환)
    public Optional<List<MemoryDTO>> getMemoryByFamilyId(UUID familyId) {
        Optional<List<Memory>> memories = memoryRepository.findByFamilyId(familyId);
        if (memories.isPresent()) {
            List<MemoryDTO> memoryDTOs = memories.get().stream()
                    .map(MemoryDTO::new) // Memory 엔티티를 MemoryDTO로 변환
                    .collect(Collectors.toList());
            return Optional.of(memoryDTOs);
        } else {
            return Optional.empty();
        }
    }

    // 추억 아이디로 추억 찾기 (MemoryDTO 반환)
    public Optional<List<MemoryDTO>> getMemoryByMemoryId(UUID memoryId) {
        Optional<List<Memory>> memories = memoryRepository.findByFamilyId(memoryId);
        if (memories.isPresent()) {
            List<MemoryDTO> memoryDTOs = memories.get().stream()
                    .map(MemoryDTO::new) // Memory 엔티티를 MemoryDTO로 변환
                    .collect(Collectors.toList());
            return Optional.of(memoryDTOs);
        } else {
            return Optional.empty();
        }
    }

    // 추억 추가
    public void addMemory(Memory memory) {
        memoryRepository.save(memory);
    }

    // 추억 삭제
    public void deleteMemoryByMemoryId(UUID memoryId) {
        memoryRepository.deleteById(memoryId);
    }
}
