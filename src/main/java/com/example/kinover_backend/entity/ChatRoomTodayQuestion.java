package com.example.kinover_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@Setter
public class ChatRoomTodayQuestion {
    @Id
    @GeneratedValue
    private UUID chatRoomTodayQuestionId;

    @ManyToOne
    @JoinColumn(name = "chatRoomId")
    private ChatRoom chatRoom;

    @ManyToOne
    @JoinColumn(name = "todayQuestionId")
    private TodayQuestion todayQuestion;

    private LocalDate date;  // 질문이 적용된 날짜
}
