package com.example.kinover_backend.util;

import java.time.LocalDateTime;

/**
 * 가족 가입 시점과 알림 읽음 시각을 조합해, 멤버별로 보여 줄 알림 범위를 맞춘다.
 */
public final class NotificationMembershipCutoffs {

    private NotificationMembershipCutoffs() {
    }

    /** 알림함 목록: 가입 전 알림은 숨김(null joinedAt = 레거시 행, 기존과 동일하게 전체 허용). */
    public static boolean isVisibleForMember(LocalDateTime notificationCreatedAt, LocalDateTime familyJoinedAt) {
        if (familyJoinedAt == null || notificationCreatedAt == null) {
            return true;
        }
        return !notificationCreatedAt.isBefore(familyJoinedAt);
    }

    /** 벨 미읽음: 읽음 처리 시각과 가입 시각 중 더 늦은 시점 이후만 미읽음으로 센다. */
    public static LocalDateTime bellUnreadLowerBound(LocalDateTime lastNotificationCheckedAt, LocalDateTime familyJoinedAt) {
        LocalDateTime readBoundary = lastNotificationCheckedAt != null ? lastNotificationCheckedAt : LocalDateTime.MIN;
        LocalDateTime joinBoundary = familyJoinedAt != null ? familyJoinedAt : LocalDateTime.MIN;
        return readBoundary.isAfter(joinBoundary) ? readBoundary : joinBoundary;
    }
}
