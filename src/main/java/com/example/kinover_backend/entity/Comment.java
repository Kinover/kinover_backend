package com.example.kinover_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Setter
@Getter
@Entity
public class Comment {
    @Id
    @GeneratedValue
    private UUID commentId;

    @Column(columnDefinition = "VARCHAR(255)")
    private String content;

    @Column(columnDefinition = "DATE")
    private Date createdAt;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "memoryId", nullable = false)
    private Memory memory;
}
