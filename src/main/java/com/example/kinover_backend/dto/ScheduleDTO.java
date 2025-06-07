package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.Schedule;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class ScheduleDTO {
    private UUID scheduleId;
    private String title;
    private String memo;
    private boolean isPersonal;
    private LocalDate date;
    private Long userId;
    private String userName;// User ID
    private UUID familyId;  // Family ID

    // Schedule 엔티티를 ScheduleDTO로 변환하는 생성자
    public ScheduleDTO(Schedule schedule) {
        if(schedule.getScheduleId()==null){
            schedule.setScheduleId(UUID.randomUUID());
        }
        if(schedule.getUser()!=null){
            this.userId = schedule.getUser().getUserId();   // User 엔티티에서 userId를 가져옴
            this.userName = schedule.getUser().getName();   // User 엔티티에서 userId를 가져옴
        }
        this.scheduleId = schedule.getScheduleId();
        this.title = schedule.getTitle();
        this.memo = schedule.getMemo();
        this.isPersonal = schedule.isPersonal();
        this.date = schedule.getDate();
        this.familyId = schedule.getFamily().getFamilyId(); // Family 엔티티에서 familyId를 가져옴
    }
}
