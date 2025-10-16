package com.example.kinover_backend.websocket;

import com.example.kinover_backend.JwtUtil;
import com.example.kinover_backend.service.UserService;
import com.example.kinover_backend.entity.Family;
import com.example.kinover_backend.entity.User;
import com.example.kinover_backend.repository.UserFamilyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.context.annotation.Lazy;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@RequiredArgsConstructor
public class WebSocketStatusHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    @Lazy
    private final UserService userService;
    private final UserFamilyRepository userFamilyRepository;

    // 사용자별 다중 세션 관리
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        URI uri = session.getUri();
        System.out.println("[WS /status] 연결 요청: " + uri);

        String token = validateToken(session); // jwt 토큰 유효성 검사
        Long userId = jwtUtil.getUserIdFromToken(token);

        System.out.println("[WS /status] userId 파싱 성공: " + userId);

        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);

        // 2) 가족 구성원 온라인 상태 동기화 (연결이 없는 사람은 OFF)
        syncFamilyOnlineStates(userId);

        userService.updateUserOnlineStatus(userId, true, true);  // ✅ 수정된 메서드 사용

        System.out.println("[WS /status] 세션 등록 및 온라인 처리 완료");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String token = getQueryParam(Objects.requireNonNull(session.getUri()), "token");
        Long userId = jwtUtil.getUserIdFromToken(token);

        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                userService.updateUserOnlineStatus(userId, false, true); // ✅ 수정된 메서드 사용
            }
        }
    }

     /** 접속자(userId)가 속한 모든 가족의 멤버를 조회해,
     *  세션이 ‘실제로’ 없는 멤버만 offline 으로 만든다. */
    private void syncFamilyOnlineStates(Long userId) {
        // 유저가 속한 가족들 (보통 1개라고 하셨음)
        List<Family> families = userFamilyRepository.findFamiliesByUserId(userId);

        for (Family family : families) {
            // 가족의 모든 사용자
            List<User> members = userFamilyRepository.findUsersByFamilyId(family.getFamilyId());
            for (User member : members) {
                Long memberId = member.getUserId();

                // 자기 자신은 이미 online 처리했으므로 스킵(원하면 true 재설정도 가능)
                if (Objects.equals(memberId, userId)) continue;

                if (!isUserConnected(memberId)) {
                    // 연결 세션이 하나도 없으면 OFF
                    userService.updateUserOnlineStatus(memberId, false, false);
                    System.out.println("[WS /status] 가족 동기화: memberId=" + memberId + " → offline");
                } else {
                    // (선택) 연결이 있으면 ON으로 재보정하고 싶다면 아래 주석 해제
                    // userService.updateUserOnlineStatus(memberId, true);
                }
            }
        }
    }

    /** sessions 맵 기준으로 해당 유저가 ‘현재’ 연결되어 있는지 확인 */
    private boolean isUserConnected(Long userId) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null || set.isEmpty()) return false;
        // 좀비 세션 방지: 실제 open 인 세션이 하나라도 있으면 연결 중으로 판단
        return set.stream().anyMatch(WebSocketSession::isOpen);
    }

    private String validateToken(WebSocketSession session) {
        URI uri = session.getUri();
        String token = getQueryParam(uri, "token");
        System.out.println("[WS] 받은 토큰: " + token);

        if (token == null || !jwtUtil.isTokenValid(token)) {
            throw new RuntimeException("Invalid or expired JWT token");
        }

        return token;
    }

    private String getQueryParam(URI uri, String key) {
        String query = uri.getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2 && pair[0].equals(key)) {
                return pair[1];
            }
        }
        return null;
    }

    public Set<WebSocketSession> getSessionsByUserId(Long userId) {
        return sessions.getOrDefault(userId, Set.of());
    }
}