package com.example.kinover_backend.service;

import com.example.kinover_backend.controller.BadRequestException;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.entity.UserBlock;
import com.example.kinover_backend.repository.UserBlockRepository;
import com.example.kinover_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;

    @Transactional
    public void blockUser(Long blockerId, Long blockedId) {
        if (blockerId == null || blockedId == null) {
            throw new BadRequestException("blockerId와 blockedUserId는 필수입니다.");
        }
        if (blockerId.equals(blockedId)) {
            throw new BadRequestException("자기 자신은 차단할 수 없습니다.");
        }
        if (!userRepository.existsById(blockedId)) {
            throw new BadRequestException("대상 유저를 찾을 수 없습니다.");
        }
        if (userBlockRepository.existsByBlocker_UserIdAndBlocked_UserId(blockerId, blockedId)) {
            return;
        }

        User blocker = userRepository.getReferenceById(blockerId);
        User blocked = userRepository.getReferenceById(blockedId);

        UserBlock row = new UserBlock();
        row.setBlocker(blocker);
        row.setBlocked(blocked);
        try {
            userBlockRepository.save(row);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청 등으로 유니크 제약이 걸린 경우 무시
        }
    }

    @Transactional
    public void unblockUser(Long blockerId, Long blockedId) {
        if (blockerId == null || blockedId == null) {
            throw new BadRequestException("blockerId와 blockedUserId는 필수입니다.");
        }
        userBlockRepository.deleteByBlocker_UserIdAndBlocked_UserId(blockerId, blockedId);
    }
}
