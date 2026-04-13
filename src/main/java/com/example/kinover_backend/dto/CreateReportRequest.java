package com.example.kinover_backend.dto;

import com.example.kinover_backend.enums.ReportReasonCode;
import com.example.kinover_backend.enums.ReportTargetType;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateReportRequest {

    private ReportTargetType targetType;
    /** POST, COMMENT, MESSAGE, SCHEDULE 대상 ID */
    private UUID targetUuid;
    /** USER 신고 시 프로필 유저 ID */
    private Long targetUserId;
    private ReportReasonCode reasonCode;
}
