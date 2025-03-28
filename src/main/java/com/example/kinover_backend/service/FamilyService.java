package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.FamilyDTO;
import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.repository.FamilyRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FamilyService {

    private final FamilyRepository familyRepository;

    @Autowired
    public FamilyService(FamilyRepository familyRepository) {
        this.familyRepository = familyRepository;
    }

    // [C] 가족 생성
    public FamilyDTO addFamily(Family family) {
        Family savedFamily = familyRepository.save(family);
        return new FamilyDTO(savedFamily); // 저장된 Family 엔티티를 FamilyDTO로 변환하여 반환
    }

    // [R] 가족 조회 (DTO 반환)
    public FamilyDTO getFamilyById(UUID familyId) {
        Family family = familyRepository.findFamilyById(familyId);

        // 패밀리 객체가 없으면 예외를 던지기
        if (family == null) {
            throw new EntityNotFoundException("Family with ID " + familyId + " not found.");
        }
        return new FamilyDTO(family); // Family 엔티티를 FamilyDTO로 변환하여 반환
    }

    // [D] 가족 삭제
    public void deleteFamily(UUID familyId) {
        familyRepository.deleteById(familyId);
    }

    // 가족 정보 수정
    public FamilyDTO modifyFamily(Family family){
        FamilyDTO familyDto= new FamilyDTO(familyRepository.save(family));
        return familyDto;
    }
}
