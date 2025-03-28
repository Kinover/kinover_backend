package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {

    @Query("select c from Challenge c where c.family.familyId=:familyId")
    List<Challenge> getChallengesByFamilyId(@Param("familyId")UUID familyId);

    @Query("select c from Challenge c where c.challengeId=:challengeId")
    Challenge getChallengeByChallengeId(@Param("challengeId") UUID challengeId);

    @Query("SELECT c FROM Challenge c WHERE c.family.familyId = :familyId ORDER BY c.createdAt DESC")
    Challenge getChallengeByFamilyId(@Param("familyId") UUID familyId);


}
