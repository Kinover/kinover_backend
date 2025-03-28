package com.example.kinover_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@Entity
public class Memo {
    @Id
    @GeneratedValue
    private UUID MemoId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "DATE")
    private Date createdAt;

}
