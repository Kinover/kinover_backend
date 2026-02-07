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

    // ✅ familyId 내려줘야 프론트가 hasFamily 판단 가능
    private UUID familyId;

    // 엔티티 → DTO 변환
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

        // ✅✅✅ 핵심: familyId 세팅
        // 1) User 엔티티에 familyId(UUID) 필드가 있으면 이게 정답
        // (아래 getFamilyId()가 컴파일 안 되면 2)로 바꿔)
        // ✅ userFamilyList -> family -> familyId
        // ✅✅✅ 핵심: 가족이 여러 개면 "가장 최근 생성된 가족" 선택
        if (user.getUserFamilyList() != null && !user.getUserFamilyList().isEmpty()) {

            var latest = user.getUserFamilyList().stream()
                    .filter(uf -> uf != null && uf.getFamily() != null)
                    .max((a, b) -> {
                        // Family.createdAt 타입에 맞춰서 getCreatedAt() 비교
                        var at = a.getFamily().getCreatedAt();
                        var bt = b.getFamily().getCreatedAt();

                        if (at == null && bt == null)
                            return 0;
                        if (at == null)
                            return -1;
                        if (bt == null)
                            return 1;

                        // Date면 compareTo 가능, LocalDateTime도 compareTo 가능
                        return at.compareTo(bt);
                    })
                    .orElse(null);

            if (latest != null) {
                this.familyId = latest.getFamily().getFamilyId();
            }
        }

        // 2) 만약 관계(User -> Family)로만 들고 있고 familyId 필드가 없으면:
        // this.familyId = (user.getFamily() != null) ? user.getFamily().getFamilyId() :
        // null;
    }
}
