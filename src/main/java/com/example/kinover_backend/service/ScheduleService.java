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
        Long userId = dto.getUserId(); // 조회 필터용(선택)

        if (familyId == null || date == null) {
            throw new IllegalArgumentException("familyId, date는 필수입니다.");
        }

        List<Schedule> schedules = scheduleRepository.findVisibleSchedulesByFilter(familyId, date, userId);

        // ✅ DTO 생성자에서 participantNames까지 채워짐
        return schedules.stream().map(ScheduleDTO::new).toList();
    }

    @Transactional
    public UUID addSchedule(ScheduleDTO dto) {
        List<Long> normalizedIds = coerceToLongList(dto.getParticipantIds());
        validateUpsert(dto, normalizedIds);

        Family family = familyRepository.findById(dto.getFamilyId())
                .orElseThrow(() -> new RuntimeException("가족(familyId)를 찾을 수 없습니다."));

        Schedule schedule = new Schedule();
        schedule.setFamily(family);

        schedule.setTitle(dto.getTitle());
        schedule.setMemo(dto.getMemo());
        schedule.setDate(dto.getDate());
        schedule.setType(dto.getType());

        boolean computedPersonal = dto.getType() == ScheduleType.INDIVIDUAL && normalizedIds.size() == 1;
        boolean finalPersonal = dto.getPersonal() != null ? dto.getPersonal() : computedPersonal;
        schedule.setPersonal(finalPersonal);

        Long loginUserId = getAuthenticatedUserIdOrThrow();
        User createdBy = userRepository.findById(loginUserId)
                .orElseThrow(() -> new RuntimeException("작성자(userId)를 찾을 수 없습니다."));
        schedule.setCreatedBy(createdBy);

        schedule.setParticipants(resolveParticipants(dto.getType(), normalizedIds));

        scheduleRepository.save(schedule);
        return schedule.getScheduleId();
    }

    @Transactional
    public UUID modifySchedule(ScheduleDTO dto) {
        if (dto.getScheduleId() == null) {
            throw new IllegalArgumentException("scheduleId는 필수입니다.");
        }

        List<Long> normalizedIds = coerceToLongList(dto.getParticipantIds());
        validateUpsert(dto, normalizedIds);

        Schedule schedule = scheduleRepository.findById(dto.getScheduleId())
                .orElseThrow(() -> new RuntimeException("수정할 일정을 찾을 수 없습니다."));

        schedule.setTitle(dto.getTitle());
        schedule.setMemo(dto.getMemo());
        schedule.setDate(dto.getDate());
        schedule.setType(dto.getType());

        boolean computedPersonal = dto.getType() == ScheduleType.INDIVIDUAL && normalizedIds.size() == 1;
        boolean finalPersonal = dto.getPersonal() != null ? dto.getPersonal() : computedPersonal;
        schedule.setPersonal(finalPersonal);

        schedule.setParticipants(resolveParticipants(dto.getType(), normalizedIds));

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

        List<Schedule> schedules = scheduleRepository.findByFamily_FamilyIdAndDateBetween(familyId, startDate, endDate);

        Map<LocalDate, List<Schedule>> grouped = schedules.stream().collect(Collectors.groupingBy(Schedule::getDate));

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

    private Long getAuthenticatedUserIdOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new RuntimeException("인증 정보가 없습니다. (Authentication null)");
        }

        Object principal = auth.getPrincipal();
        if (principal == null) {
            throw new RuntimeException("인증 principal이 없습니다. (principal null)");
        }

        if (principal instanceof Long userId) {
            return userId;
        }

        if (principal instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (Exception e) {
                throw new RuntimeException("principal String을 userId로 파싱 실패: " + s);
            }
        }

        throw new RuntimeException("principal 타입이 예상과 다릅니다: " + principal.getClass());
    }

    private void validateUpsert(ScheduleDTO dto, List<Long> normalizedIds) {
        if (dto.getFamilyId() == null)
            throw new IllegalArgumentException("familyId는 필수입니다.");
        if (dto.getDate() == null)
            throw new IllegalArgumentException("date는 필수입니다.");
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty())
            throw new IllegalArgumentException("title은 필수입니다.");
        if (dto.getType() == null)
            throw new IllegalArgumentException("type은 필수입니다.");

        if (dto.getType() == ScheduleType.ANNIVERSARY) {
            if (!normalizedIds.isEmpty()) {
                throw new IllegalArgumentException("ANNIVERSARY는 participantIds를 보낼 수 없습니다.");
            }
        } else {
            if (normalizedIds.isEmpty()) {
                throw new IllegalArgumentException(dto.getType() + "는 participantIds가 1명 이상 필요합니다.");
            }
        }
    }

    private Set<User> resolveParticipants(ScheduleType type, List<Long> participantIds) {
        if (type == ScheduleType.ANNIVERSARY) {
            return new HashSet<>();
        }

        if (participantIds == null || participantIds.isEmpty()) {
            return new HashSet<>();
        }

        List<User> users = userRepository.findAllById(participantIds);
        if (users.size() != participantIds.size()) {
            Set<Long> found = users.stream().map(User::getUserId).collect(Collectors.toSet());
            List<Long> missing = participantIds.stream().filter(id -> !found.contains(id)).toList();
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
