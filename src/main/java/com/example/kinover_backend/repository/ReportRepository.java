package com.example.kinover_backend.repository;

import com.example.kinover_backend.entity.Report;
import com.example.kinover_backend.enums.ReportStatus;
import com.example.kinover_backend.enums.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    long countByTargetTypeAndTargetPostIdAndStatus(
            ReportTargetType targetType,
            UUID targetPostId,
            ReportStatus status
    );

    long countByTargetTypeAndTargetCommentIdAndStatus(
            ReportTargetType targetType,
            UUID targetCommentId,
            ReportStatus status
    );

    long countByTargetTypeAndTargetMessageIdAndStatus(
            ReportTargetType targetType,
            UUID targetMessageId,
            ReportStatus status
    );

    long countByTargetTypeAndTargetScheduleIdAndStatus(
            ReportTargetType targetType,
            UUID targetScheduleId,
            ReportStatus status
    );

    long countByTargetTypeAndTargetUserIdAndStatus(
            ReportTargetType targetType,
            Long targetUserId,
            ReportStatus status
    );
}
