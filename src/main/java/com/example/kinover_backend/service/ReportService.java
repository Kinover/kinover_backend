package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.CreateReportRequest;
import com.example.kinover_backend.entity.Report;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.enums.ReportStatus;
import com.example.kinover_backend.enums.ReportTargetType;
import com.example.kinover_backend.enums.ReportTargetType;
import com.example.kinover_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final MessageRepository messageRepository;
    private final ScheduleRepository scheduleRepository;

    @Value("${moderation.report.auto-hide-threshold:3}")
    private int autoHideThreshold;

    @Transactional
    public void createReport(Long reporterId, CreateReportRequest req) {
        if (reporterId == null) {
            throw new IllegalArgumentException("reporterId is null");
        }
        if (req == null || req.getTargetType() == null || req.getReasonCode() == null) {
            throw new IllegalArgumentException("targetType과 reasonCode는 필수입니다.");
        }

        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new IllegalArgumentException("신고자 정보를 찾을 수 없습니다."));

        Report report = new Report();
        report.setReporter(reporter);
        report.setTargetType(req.getTargetType());
        report.setReasonCode(req.getReasonCode());
        report.setStatus(ReportStatus.PENDING);

        switch (req.getTargetType()) {
            case POST -> {
                if (req.getTargetUuid() == null) {
                    throw new IllegalArgumentException("POST 신고에는 targetUuid(게시글 ID)가 필요합니다.");
                }
                postRepository.findById(req.getTargetUuid())
                        .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
                report.setTargetPostId(req.getTargetUuid());
            }
            case COMMENT -> {
                if (req.getTargetUuid() == null) {
                    throw new IllegalArgumentException("COMMENT 신고에는 targetUuid(댓글 ID)가 필요합니다.");
                }
                commentRepository.findById(req.getTargetUuid())
                        .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
                report.setTargetCommentId(req.getTargetUuid());
            }
            case MESSAGE -> {
                if (req.getTargetUuid() == null) {
                    throw new IllegalArgumentException("MESSAGE 신고에는 targetUuid(메시지 ID)가 필요합니다.");
                }
                messageRepository.findById(req.getTargetUuid())
                        .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));
                report.setTargetMessageId(req.getTargetUuid());
            }
            case SCHEDULE -> {
                if (req.getTargetUuid() == null) {
                    throw new IllegalArgumentException("SCHEDULE 신고에는 targetUuid(일정 ID)가 필요합니다.");
                }
                scheduleRepository.findById(req.getTargetUuid())
                        .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다."));
                report.setTargetScheduleId(req.getTargetUuid());
            }
            case USER -> {
                if (req.getTargetUserId() == null) {
                    throw new IllegalArgumentException("USER 신고에는 targetUserId가 필요합니다.");
                }
                if (req.getTargetUserId().equals(reporterId)) {
                    throw new IllegalArgumentException("본인을 신고할 수 없습니다.");
                }
                userRepository.findById(req.getTargetUserId())
                        .orElseThrow(() -> new IllegalArgumentException("신고 대상 유저를 찾을 수 없습니다."));
                report.setTargetUserId(req.getTargetUserId());
            }
        }

        reportRepository.save(report);
        applyAutoHideIfThresholdReached(req.getTargetType(), req.getTargetUuid());
    }

    private void applyAutoHideIfThresholdReached(ReportTargetType type, UUID targetUuid) {
        if (autoHideThreshold <= 0) {
            return;
        }

        long count = switch (type) {
            case POST -> reportRepository.countByTargetTypeAndTargetPostIdAndStatus(
                    ReportTargetType.POST, targetUuid, ReportStatus.PENDING);
            case COMMENT -> reportRepository.countByTargetTypeAndTargetCommentIdAndStatus(
                    ReportTargetType.COMMENT, targetUuid, ReportStatus.PENDING);
            case MESSAGE -> reportRepository.countByTargetTypeAndTargetMessageIdAndStatus(
                    ReportTargetType.MESSAGE, targetUuid, ReportStatus.PENDING);
            case SCHEDULE -> reportRepository.countByTargetTypeAndTargetScheduleIdAndStatus(
                    ReportTargetType.SCHEDULE, targetUuid, ReportStatus.PENDING);
            case USER -> 0L;
        };

        if (count < autoHideThreshold) {
            return;
        }

        switch (type) {
            case POST -> postRepository.findById(targetUuid).ifPresent(p -> {
                p.setHidden(true);
                postRepository.save(p);
            });
            case COMMENT -> commentRepository.findById(targetUuid).ifPresent(c -> {
                c.setHidden(true);
                commentRepository.save(c);
            });
            case MESSAGE -> messageRepository.findById(targetUuid).ifPresent(m -> {
                m.setHidden(true);
                messageRepository.save(m);
            });
            case SCHEDULE -> scheduleRepository.findById(targetUuid).ifPresent(s -> {
                s.setHidden(true);
                scheduleRepository.save(s);
            });
            default -> {
            }
        }
    }
}
