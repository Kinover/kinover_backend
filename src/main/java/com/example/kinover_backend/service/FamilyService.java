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

    public FamilyDTO createFamily() {
        Family family = new Family();

        // 1. UUID: Entity의 @GeneratedValue 전략에 의해 DB 저장 시 자동 생성됨
        // 2. Name: 요구하신 대로 대충 넣어줍니다 (기본값 설정)
        family.setName("내 가족"); 

        // 3. Relationship, Notice: 요구하신 대로 Null (설정 안 하면 null)
        family.setRelationship(null);
        family.setNotice(null);

        // 4. 저장 (이 시점에 UUID가 생성됨)
        Family savedFamily = familyRepository.save(family);

        // 5. DTO 변환 및 반환
        return new FamilyDTO(savedFamily);
    }

    // [R] 가족 조회 (DTO 반환)
    public FamilyDTO getFamilyById(UUID familyId) {

        Family family = familyRepository.findFamilyById(familyId)
                .orElseThrow(() -> new EntityNotFoundException("Family with ID " + familyId + " not found."));

        return new FamilyDTO(family); // Family 엔티티를 FamilyDTO로 변환하여 반환
    }

    // [D] 가족 삭제
    public void deleteFamily(UUID familyId) {
        familyRepository.deleteById(familyId);
    }

    // 가족 정보 수정
    public FamilyDTO modifyFamily(Family family){
        return new FamilyDTO(familyRepository.save(family));
    }

    public String getNotice(UUID familyId) {
        return familyRepository.findById(familyId)
                .map(Family::getNotice)
                .orElse(""); // 공지가 없으면 빈 문자열
    }

    public void updateNotice(UUID familyId, String content) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new IllegalArgumentException("해당 가족이 존재하지 않습니다."));
        family.setNotice(content);
        familyRepository.save(family);
    }
}
