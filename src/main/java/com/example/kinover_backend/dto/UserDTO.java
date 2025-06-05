package com.example.kinover_backend.dto;

import com.example.kinover_backend.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.webjars.NotFoundException;

import java.util.Date;
import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
public class UserDTO {
    private Long userId;
    private String name;
    private Date birth;
    private String email;
    private String emotion;
    private String image;
    private String phoneNumber;
    private String trait;

    // 엔티티 → DTO 변환
    public UserDTO(User user) {
//        if (user == null) {
//            throw new NotFoundException("Family not found for id: " + userId);
//        }
        this.userId = user.getUserId();
        this.name = user.getName();
        this.birth = user.getBirth();
        this.email = user.getEmail();
        this.emotion = user.getEmotion();
        this.image = user.getImage();
        this.phoneNumber = user.getPhoneNumber();
        this.trait = user.getTrait();
    }
}
