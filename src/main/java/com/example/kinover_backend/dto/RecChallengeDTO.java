package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.RecChallenge;
import com.example.kinover_backend.enums.ChallengeCategory;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
public class RecChallengeDTO {
    private UUID recChallengeId;
    private String title;
    private List<String> categories;
    private String duration;
    private int daysPerWeek;
    private int dailyTimeInMinutes;
    private String rewardDescription;
    private String taskDescription;
    private String image;

    // RecChallenge 엔티티를 RecChallengeDTO로 변환하는 생성자
    public RecChallengeDTO(RecChallenge recChallenge) {
        if(recChallenge.getRecChallengeId()==null){
            recChallenge.setRecChallengeId(UUID.randomUUID());
        }
        this.recChallengeId = recChallenge.getRecChallengeId();
        this.title = recChallenge.getTitle();
        this.categories = recChallenge.getCategories();
        this.duration = recChallenge.getDuration();
        this.daysPerWeek = recChallenge.getDaysPerWeek();
        this.dailyTimeInMinutes = recChallenge.getDailyTimeInMinutes();
        this.rewardDescription = recChallenge.getRewardDescription();
        this.taskDescription = recChallenge.getTaskDescription();
        this.image=recChallenge.getImage();
    }
}
