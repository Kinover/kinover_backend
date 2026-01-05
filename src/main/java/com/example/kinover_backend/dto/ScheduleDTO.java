// src/main/java/com/example/kinover_backend/dto/ScheduleDTO.java
package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.Schedule;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.enums.ScheduleType;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
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

    // ✅ 필수
    private ScheduleType type;

    // ✅ 참여자(다중)
    private List<Long> participantIds;
    private List<String> participantNames;

    // ✅ 조회 필터용(선택)
    private Long userId;

    private UUID familyId;

    public ScheduleDTO(Schedule schedule) {
        this.scheduleId = schedule.getScheduleId();
        this.title = schedule.getTitle();
        this.memo = schedule.getMemo();
        this.date = schedule.getDate();
        this.type = schedule.getType();

        if (schedule.getParticipants() != null) {
            this.participantIds = schedule.getParticipants().stream()
                .map(User::getUserId)
                .toList();

            this.participantNames = schedule.getParticipants().stream()
                .map(u -> u.getName() == null ? "" : u.getName())
                .toList();
        }

        if (schedule.getFamily() != null) {
            this.familyId = schedule.getFamily().getFamilyId();
        }
    }
}
