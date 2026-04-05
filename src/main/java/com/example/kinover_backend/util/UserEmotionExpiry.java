package com.example.kinover_backend.util;

import com.example.kinover_backend.entity.User;

import java.time.LocalDateTime;

/**
 * 감정(emotion)은 설정 시점({@link User#getEmotionUpdatedAt()})으로부터 24시간이 지나면 만료된 것으로 본다.
 */
public final class UserEmotionExpiry {

    public static final int EMOTION_TTL_HOURS = 24;

    private UserEmotionExpiry() {
    }

    /** DB에 저장된 값 기준으로 감정 표시/유효 기간이 지났는지 여부 */
    public static boolean isStale(User user) {
        if (user == null || user.getEmotion() == null) {
            return false;
        }
        LocalDateTime updatedAt = user.getEmotionUpdatedAt();
        LocalDateTime now = LocalDateTime.now();
        return updatedAt == null || updatedAt.isBefore(now.minusHours(EMOTION_TTL_HOURS));
    }
}
