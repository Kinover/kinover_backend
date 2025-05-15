package com.example.kinover_backend.entity;

import com.example.kinover_backend.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
public class UserFamily {
    @Id
    @GeneratedValue
    private UUID userFamilyId;

    @Column
    private String role;

    @ManyToOne
    @JoinColumn(name = "user_id",  nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

}
