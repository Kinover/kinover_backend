package com.example.kinover_backend.service;

import com.example.kinover_backend.entity.Challenge;
import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.entity.RecChallenge;
import com.example.kinover_backend.repository.ChallengeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ChallengeService {
    private final ChallengeRepository challengeRepository;

    @Autowired
    public ChallengeService(ChallengeRepository challengeRepository) {
        this.challengeRepository = challengeRepository;
    }

    // 특정 가족의 모든 챌린지 제공
    public List<Challenge> getChallengesByFamilyId(UUID familyId){
        return challengeRepository.getChallengesByFamilyId(familyId);
    }

    // 특정 가족의 가장 최근 챌린지 (현재 설정된 챌린지) 제공
    public Challenge getChallengeByFamilyId(UUID familyId){
        return challengeRepository.getChallengeByFamilyId(familyId);
    }

    // 특정 가족의 챌린지 삭제
    public void deleteChallenge(UUID challengeId){
        challengeRepository.deleteById(challengeId);
    }

    // 특정 가족의 챌린지 삭제
    public Challenge saveChallengeByFamilyId(Family family, RecChallenge recChallenge){
        Challenge challenge = new Challenge(family,recChallenge);
        return challengeRepository.save(challenge);
    }
}
