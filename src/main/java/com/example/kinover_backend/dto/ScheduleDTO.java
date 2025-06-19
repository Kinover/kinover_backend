package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.Schedule;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ScheduleDTO {

    private UUID scheduleId;
    private String title;
    private String memo;
    private LocalDate date;
    private boolean Personal;
    private Long userId;
    private String userName;
    private UUID familyId;

    // 엔티티 → DTO 변환용 생성자
    public ScheduleDTO(Schedule schedule) {
        this.scheduleId = schedule.getScheduleId();
        this.title = schedule.getTitle();
        this.memo = schedule.getMemo();
        this.date = schedule.getDate();
        this.Personal = schedule.isPersonal();

        if (schedule.getUser() != null) {
            this.userId = schedule.getUser().getUserId();
            this.userName = schedule.getUser().getName();
        }

        if (schedule.getFamily() != null) {
            this.familyId = schedule.getFamily().getFamilyId();
        }
    }
}
