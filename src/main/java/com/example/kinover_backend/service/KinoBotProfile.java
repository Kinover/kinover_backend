package com.example.kinover_backend.service;

import com.example.kinover_backend.enums.ChatBotPersonality;
import com.example.kinover_backend.enums.KinoType;

public final class KinoBotProfile {

    public static final Long KINO_USER_ID = 9999999999L;
    public static final String KINO_USER_NAME = "키노";
    public static final String KINO_ROOM_NAME = "키노상담소";

    private KinoBotProfile() {
    }

    public static ChatBotPersonality normalizePersonality(ChatBotPersonality personality) {
        return personality != null ? personality : ChatBotPersonality.SUNNY;
    }

    public static KinoType kinoTypeFor(ChatBotPersonality personality) {
        return switch (normalizePersonality(personality)) {
            case SUNNY -> KinoType.YELLOW_KINO;
            case SERENE -> KinoType.BLUE_KINO;
            case SNUGGLE -> KinoType.PINK_KINO;
        };
    }

    public static String openingMessage(ChatBotPersonality personality) {
        return switch (normalizePersonality(personality)) {
            case SUNNY ->
                    "안녕하세요! 저는 키노에요! 가족들이 하루 동안 느낀 일들, 나누고 싶은 순간들, "
                            + "그 모든 따뜻한 기록을 한곳에 모아주는 역할을 하고 있어요. "
                            + "여기선 무엇이든 편하게 말해줘요. 다 소중한 이야기니까요!";
            case SERENE ->
                    "안녕하세요. 저는 키노예요. 가족들이 하루 동안 느낀 일들, 나누고 싶은 순간들, "
                            + "그 모든 따뜻한 기록을 한곳에 차분히 모아두는 역할을 하고 있어요. "
                            + "여기서는 어떤 이야기든 편하게 들려주세요. 모두 소중한 마음이니까요.";
            case SNUGGLE ->
                    "안녕하세요... 저는 키노예요. 가족들이 하루 동안 느낀 일들, 나누고 싶은 순간들, "
                            + "그 모든 따뜻한 기록을 포근하게 한곳에 모아두고 있어요. "
                            + "여기서는 무엇이든 편하게 말해줘요. 기쁜 하루도, 속상한 마음도 다 소중한 이야기니까요.";
        };
    }
}
