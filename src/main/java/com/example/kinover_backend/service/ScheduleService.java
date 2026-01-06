// src/main/java/com/example/kinover_backend/service/ScheduleService.java
package com.example.kinover_backend.service;

import com.example.kinover_backend.dto.ScheduleDTO;
import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.entity.Schedule;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.enums.ScheduleType;
import com.example.kinover_backend.repository.FamilyRepository;
import com.example.kinover_backend.repository.ScheduleRepository;
import com.example.kinover_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;

    @Transactional(readOnly = true)
    public List<ScheduleDTO> getSchedulesByFilter(ScheduleDTO dto) {
        UUID familyId = dto.getFamilyId();
        LocalDate date = dto.getDate();
        Long userId = dto.getUserId(); // ✅ 조회 필터용(선택) - 프론트가 필요하면 보내도 됨

        if (familyId == null || date == null) {
            throw new IllegalArgumentException("familyId, date는 필수입니다.");
        }

        List<Schedule> schedules =
                scheduleRepository.findVisibleSchedulesByFilter(familyId, date, userId);

        return schedules.stream().map(ScheduleDTO::new).toList();
    }

    @Transactional
    public UUID addSchedule(ScheduleDTO dto) {
        // ✅ DTO 기반 검증(ANNIVERSARY participantIds 금지 / 그 외 1명 이상)
        validateUpsert(dto);

        Family family = familyRepository.findById(dto.getFamilyId())
                .orElseThrow(() -> new RuntimeException("가족(familyId)를 찾을 수 없습니다."));

        Schedule schedule = new Schedule();
        schedule.setFamily(family);

        schedule.setTitle(dto.getTitle());
        schedule.setMemo(dto.getMemo());
        schedule.setDate(dto.getDate());
        schedule.setType(dto.getType());

        // ✅ 작성자는 "프론트 userId"가 아니라 "JWT(SecurityContext)"에서 가져온다
        Long loginUserId = getAuthenticatedUserIdOrThrow();
        User createdBy = userRepository.findById(loginUserId)
                .orElseThrow(() -> new RuntimeException("작성자(userId)를 찾을 수 없습니다."));
        schedule.setCreatedBy(createdBy);

        // ✅ 참여자 세팅
        schedule.setParticipants(resolveParticipants(dto.getType(), dto.getParticipantIds()));

        scheduleRepository.save(schedule);
        return schedule.getScheduleId();
    }

    @Transactional
    public UUID modifySchedule(ScheduleDTO dto) {
        if (dto.getScheduleId() == null) {
            throw new IllegalArgumentException("scheduleId는 필수입니다.");
        }

        validateUpsert(dto);

        Schedule schedule = scheduleRepository.findById(dto.getScheduleId())
                .orElseThrow(() -> new RuntimeException("수정할 일정을 찾을 수 없습니다."));

        schedule.setTitle(dto.getTitle());
        schedule.setMemo(dto.getMemo());
        schedule.setDate(dto.getDate());
        schedule.setType(dto.getType());

        schedule.setParticipants(resolveParticipants(dto.getType(), dto.getParticipantIds()));

        // (선택) 수정자 기록이 필요하면 여기서 updatedBy 같은 필드에 세팅
        // Long loginUserId = getAuthenticatedUserIdOrThrow();

        return schedule.getScheduleId();
    }

    @Transactional
    public void removeSchedule(UUID scheduleId) {
        scheduleRepository.deleteById(scheduleId);
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, Map<String, Long>> getScheduleCountPerDay(UUID familyId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<Schedule> schedules =
                scheduleRepository.findByFamily_FamilyIdAndDateBetween(familyId, startDate, endDate);

        Map<LocalDate, List<Schedule>> grouped =
                schedules.stream().collect(Collectors.groupingBy(Schedule::getDate));

        Map<LocalDate, Map<String, Long>> result = new HashMap<>();

        for (Map.Entry<LocalDate, List<Schedule>> e : grouped.entrySet()) {
            long individual = e.getValue().stream().filter(s -> s.getType() == ScheduleType.INDIVIDUAL).count();
            long family = e.getValue().stream().filter(s -> s.getType() == ScheduleType.FAMILY).count();
            long anniversary = e.getValue().stream().filter(s -> s.getType() == ScheduleType.ANNIVERSARY).count();
            long total = individual + family + anniversary;

            Map<String, Long> counts = new HashMap<>();
            counts.put("total", total);
            counts.put("individual", individual);
            counts.put("family", family);
            counts.put("anniversary", anniversary);

            result.put(e.getKey(), counts);
        }

        return result;
    }

    // -------------------------
    // 내부 유틸
    // -------------------------

    /**
     * ✅ JWT 인증 principal에서 userId(Long)를 꺼낸다.
     * - JwtAuthenticationFilter에서 principal로 userId(Long)를 넣어주는 구조에 맞춤
     */
    private Long getAuthenticatedUserIdOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new RuntimeException("인증 정보가 없습니다. (Authentication null)");
        }

        Object principal = auth.getPrincipal();
        if (principal == null) {
            throw new RuntimeException("인증 principal이 없습니다. (principal null)");
        }

        // ✅ 너는 principal에 Long userId를 넣고 있음
        if (principal instanceof Long userId) {
            return userId;
        }

        // ✅ 혹시 String으로 들어오는 경우 대비 (예: "4207548229")
        if (principal instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (Exception e) {
                throw new RuntimeException("principal String을 userId로 파싱 실패: " + s);
            }
        }

        throw new RuntimeException("principal 타입이 예상과 다릅니다: " + principal.getClass());
    }

    private void validateUpsert(ScheduleDTO dto) {
        if (dto.getFamilyId() == null)
            throw new IllegalArgumentException("familyId는 필수입니다.");
        if (dto.getDate() == null)
            throw new IllegalArgumentException("date는 필수입니다.");
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty())
            throw new IllegalArgumentException("title은 필수입니다.");
        if (dto.getType() == null)
            throw new IllegalArgumentException("type은 필수입니다.");

        // ✅ participantIds 타입이 애매할 수 있으니, 변환 결과 기준으로 검증
        List<Long> ids = coerceToLongList(dto.getParticipantIds());

        if (dto.getType() == ScheduleType.ANNIVERSARY) {
            if (!ids.isEmpty()) {
                throw new IllegalArgumentException("ANNIVERSARY는 participantIds를 보낼 수 없습니다.");
            }
        } else {
            if (ids.isEmpty()) {
                throw new IllegalArgumentException(dto.getType() + "는 participantIds가 1명 이상 필요합니다.");
            }
        }
    }

    /**
     * ✅ 핵심:
     * - ANNIVERSARY면 참여자 없음
     * - 그 외는 participantIds를 Long 리스트로 정규화해서 User 조회
     */
    private Set<User> resolveParticipants(ScheduleType type, List<?> participantIds) {
        if (type == ScheduleType.ANNIVERSARY) {
            return new HashSet<>();
        }

        List<Long> ids = coerceToLongList(participantIds);

        if (ids.isEmpty())
            return new HashSet<>();

        List<User> users = userRepository.findAllById(ids);
        if (users.size() != ids.size()) {
            Set<Long> found = users.stream().map(User::getUserId).collect(Collectors.toSet());
            List<Long> missing = ids.stream().filter(id -> !found.contains(id)).toList();
            throw new IllegalArgumentException("존재하지 않는 participantIds가 포함되어 있습니다: " + missing);
        }

        return new HashSet<>(users);
    }

    private List<Long> coerceToLongList(List<?> raw) {
        if (raw == null)
            return List.of();

        return raw.stream()
                .filter(Objects::nonNull)
                .map(v -> {
                    if (v instanceof Long l)
                        return l;
                    if (v instanceof Integer i)
                        return i.longValue();
                    if (v instanceof Number n)
                        return n.longValue();
                    if (v instanceof String s) {
                        String t = s.trim();
                        if (t.isEmpty())
                            return null;
                        return Long.parseLong(t);
                    }
                    throw new IllegalArgumentException("participantIds 타입이 올바르지 않습니다: " + v.getClass());
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
