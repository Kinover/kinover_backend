package com.example.kinover_backend.service;

import com.example.kinover_backend.enums.ChatBotPersonality;
import com.example.kinover_backend.enums.KinoType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KinoBotProfileTest {

    @Test
    void nullPersonalityFallsBackToSunny() {
        assertEquals(ChatBotPersonality.SUNNY, KinoBotProfile.normalizePersonality(null));
        assertEquals(KinoType.YELLOW_KINO, KinoBotProfile.kinoTypeFor(null));
        assertTrue(KinoBotProfile.openingMessage(null).contains("저는 키노에요"));
    }

    @Test
    void sereneGreetingMatchesSereneTone() {
        assertEquals(KinoType.BLUE_KINO, KinoBotProfile.kinoTypeFor(ChatBotPersonality.SERENE));
        assertTrue(KinoBotProfile.openingMessage(ChatBotPersonality.SERENE).contains("차분히"));
        assertTrue(KinoBotProfile.openingMessage(ChatBotPersonality.SERENE).contains("들려주세요"));
    }

    @Test
    void snuggleGreetingMatchesSnuggleTone() {
        assertEquals(KinoType.PINK_KINO, KinoBotProfile.kinoTypeFor(ChatBotPersonality.SNUGGLE));
        assertTrue(KinoBotProfile.openingMessage(ChatBotPersonality.SNUGGLE).contains("포근하게"));
        assertTrue(KinoBotProfile.openingMessage(ChatBotPersonality.SNUGGLE).contains("속상한 마음"));
    }
}
