package com.example.kinover_backend.dto;

import com.example.kinover_backend.enums.ChatBotPersonality;

public class UpdatePersonalityRequestDTO {
    private ChatBotPersonality personality;

    public ChatBotPersonality getPersonality() {
        return personality;
    }

    public void setPersonality(ChatBotPersonality personality) {
        this.personality = personality;
    }
}