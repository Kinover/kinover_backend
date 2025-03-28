package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.MessageDTO;
import com.example.kinover_backend.dto.RecChallengeDTO;
import com.example.kinover_backend.entity.RecChallenge;
import com.example.kinover_backend.repository.RecChallengeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecChallengeService {

    @Autowired
    private final RecChallengeRepository recChallengeRepository ;

    public RecChallengeService(RecChallengeRepository recChallengeRepository) {
        this.recChallengeRepository = recChallengeRepository;
    }

    public List<RecChallengeDTO> getRecChallenges(){
        List<RecChallenge> recChallenges = recChallengeRepository.findAll();

        return recChallenges.stream()
                .map(challenge -> new RecChallengeDTO(challenge))
                .collect(Collectors.toList());
    }
}
