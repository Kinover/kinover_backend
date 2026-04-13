package com.example.kinover_backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarketingNotificationPatchRequest {
    /** 마케팅 알림(푸시) 수신 동의 여부 */
    private Boolean isOn;
}
