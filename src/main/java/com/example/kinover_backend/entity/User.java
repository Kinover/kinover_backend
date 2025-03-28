package com.example.kinover_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Entity
public class User {
    // 유저아이디, 이름, 생년월일, 이메일, 비밀번호, 상태,
    // 프로필 이미지, 생성일자, 수정일자, 휴대폰번호

    @Id
    private Long userId;

    @Version  // 낙관적 락 적용
    private Integer version=0;

    @Column(columnDefinition = "VARCHAR(100)")
    private String name;

    @Column(columnDefinition = "DATE")
    private Date birth;

    @Column(columnDefinition = "VARCHAR(255)")
    private String email;

    @Column(columnDefinition = "VARCHAR(255)")
    private String pwd;

    @Column(columnDefinition = "VARCHAR(50)")
    private String status;

    @Column(columnDefinition = "TEXT")
    private String image;

    @Column(columnDefinition = "VARCHAR(20)")
    private String phoneNumber;

    @Column(columnDefinition = "DATE")
    private Date createdAt;

    @Column(columnDefinition = "DATE")
    private Date updatedAt;

    @OneToMany(mappedBy = "sender", fetch = FetchType.LAZY)
    private List<Message> messages = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<UserFamily> userFamilyList;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<UserChatRoom> userChatRooms; // 유저가 참여하는 여러 채팅방

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Schedule> scheduleList;  // 유저가 가진 일정들
}
