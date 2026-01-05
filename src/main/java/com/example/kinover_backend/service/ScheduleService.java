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
        Long userId = dto.getUserId();

        if (familyId == null || date == null) {
            throw new IllegalArgumentException("familyId, date는 필수입니다.");
        }

        List<Schedule> schedules = scheduleRepository.findVisibleSchedulesByFilter(familyId, date, userId);

        return schedules.stream().map(ScheduleDTO::new).toList();
    }

    @Transactional
    public UUID addSchedule(ScheduleDTO dto) {
        validateUpsert(dto);

        Family family = familyRepository.findById(dto.getFamilyId())
                .orElseThrow(() -> new RuntimeException("가족(familyId)를 찾을 수 없습니다."));

        Schedule schedule = new Schedule();
        schedule.setFamily(family);

        schedule.setTitle(dto.getTitle());
        schedule.setMemo(dto.getMemo());
        schedule.setDate(dto.getDate());
        schedule.setType(dto.getType());

        if (dto.getUserId() != null) {
            User createdBy = userRepository.findById(dto.getUserId())
                    .orElseThrow(() -> new RuntimeException("작성자(userId)를 찾을 수 없습니다."));
            schedule.setCreatedBy(createdBy);
        }

        // ✅ FAMILY에서 participantIds가 비면 ALL로 처리
        schedule.setParticipants(
                resolveParticipants(dto.getFamilyId(), dto.getType(), dto.getParticipantIds()));

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

        // ✅ 수정 시 familyId가 dto에 안 올 수도 있으니 schedule에서 보완
        UUID familyId = dto.getFamilyId();
        if (familyId == null && schedule.getFamily() != null) {
            familyId = schedule.getFamily().getFamilyId();
        }
        if (familyId == null) {
            throw new IllegalArgumentException("familyId는 필수입니다. (수정 시에도 필요)");
        }

        schedule.setParticipants(
                resolveParticipants(familyId, dto.getType(), dto.getParticipantIds()));

        // JPA dirty checking으로 save 생략 가능
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
    private void validateUpsert(ScheduleDTO dto) {
        if (dto.getFamilyId() == null)
            throw new IllegalArgumentException("familyId는 필수입니다.");
        if (dto.getDate() == null)
            throw new IllegalArgumentException("date는 필수입니다.");
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty())
            throw new IllegalArgumentException("title은 필수입니다.");
        if (dto.getType() == null)
            throw new IllegalArgumentException("type은 필수입니다.");

        // ✅ 변환 결과 기준으로 검증
        List<Long> ids = coerceToLongList(dto.getParticipantIds());

        // ✅ 규칙:
        // - ANNIVERSARY: participantIds 금지
        // - INDIVIDUAL: 1명 이상 필수
        // - FAMILY: 빈 배열/미전송 허용(=ALL)
        if (dto.getType() == ScheduleType.ANNIVERSARY) {
            if (!ids.isEmpty()) {
                throw new IllegalArgumentException("ANNIVERSARY는 participantIds를 보낼 수 없습니다.");
            }
            return;
        }

        if (dto.getType() == ScheduleType.INDIVIDUAL) {
            if (ids.isEmpty()) {
                throw new IllegalArgumentException("INDIVIDUAL은 participantIds가 1명 이상 필요합니다.");
            }
            return;
        }

        if (dto.getType() == ScheduleType.FAMILY) {
            // ✅ ALL 허용: ids가 비어도 OK
            return;
        }

        // 혹시 enum이 늘어날 미래 대비
        if (ids.isEmpty()) {
            throw new IllegalArgumentException(dto.getType() + "는 participantIds가 1명 이상 필요합니다.");
        }
    }

    /**
     * ✅ 핵심:
     * - ANNIVERSARY: 참여자 없음
     * - INDIVIDUAL: participantIds로 지정(빈 값 불가)
     * - FAMILY:
     * - participantIds 비어있으면 ALL => 해당 family의 모든 구성원으로 세팅
     * - 아니면 participantIds로 지정
     */
    private Set<User> resolveParticipants(UUID familyId, ScheduleType type, List<?> participantIds) {
        if (type == ScheduleType.ANNIVERSARY) {
            return new HashSet<>();
        }

        List<Long> ids = coerceToLongList(participantIds);

        if (type == ScheduleType.FAMILY && ids.isEmpty()) {
            // ✅ ALL: 가족 전체 구성원
            // 아래 메서드는 네 프로젝트에 맞게 repository 메서드명만 맞춰주면 됨
            List<User> familyUsers = userRepository.findByFamilyId(familyId);
            return new HashSet<>(familyUsers);
        }

        // INDIVIDUAL은 validateUpsert에서 이미 비었으면 막힘
        if (ids.isEmpty()) {
            return new HashSet<>();
        }

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
