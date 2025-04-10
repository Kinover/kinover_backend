package com.example.kinover_backend.enums;

public enum MessageType {
    TEXT("text"),
    IMAGE("image"),
    VIDEO("video");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}