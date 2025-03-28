package com.example.kinover_backend.enums;

public enum FamilyRelationship {
    AWKWARD_START("어색한 사이"),
    GETTING_TO_KNOW("알아가는 사이"),
    GENTLE_APPROACH("다가가는 사이"),
    COMFORTABLE_DISTANCE("편안한 사이"),
    SHARING_HEARTS("진심을 나누는 사이"),
    SOLID_BOND("단단한 사이"),
    FAMILY_OF_TRUST("믿음의 사이"),
    UNIFIED_HEARTS("하나된 사이");

    private final String description; // 한글 설명 필드

    FamilyRelationship(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
