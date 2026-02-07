package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.enums.UserEmotion;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class UserDTO {
    private Long userId;
    private String name;
    private Date birth;
    private String email;
    private UserEmotion emotion;
    private LocalDateTime emotionUpdatedAt;
    private String image;
    private String phoneNumber;
    private String trait;
    private UUID familyId; // ✅ 내려줄 필드

    public UserDTO(User user) {
        this.userId = user.getUserId();
        this.name = user.getName();
        this.birth = user.getBirth();
        this.email = user.getEmail();
        this.emotion = user.getEmotion();
        this.image = user.getImage();
        this.phoneNumber = user.getPhoneNumber();
        this.trait = user.getTrait();
        this.emotionUpdatedAt = user.getEmotionUpdatedAt();
        // ❌ familyId는 여기서 건드리지 말기
    }
}
