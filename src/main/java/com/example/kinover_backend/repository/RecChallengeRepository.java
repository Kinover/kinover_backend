package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.RecChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecChallengeRepository extends JpaRepository<RecChallenge, UUID> {

    @Query("SELECT rc FROM RecChallenge rc")
    List<RecChallenge> getRecChallenges();

}
