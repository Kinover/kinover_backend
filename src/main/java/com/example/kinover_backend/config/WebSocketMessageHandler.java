package com.example.kinover_backend.config;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component // 이 어노테이션을 추가하여 빈으로 등록
public class WebSocketMessageHandler implements WebSocketHandler {

    // 웹소켓 세션을 저장할 Set (접속한 클라이언트 관리)
    private static final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 클라이언트가 연결되었을 때 세션을 추가
        sessions.add(session);
        System.out.println("웹소켓 연결 성공: " + session.getId());
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        // 클라이언트로부터 받은 메시지를 처리
        System.out.println("클라이언트로부터 받은 메시지: " + message.getPayload());

        // 받은 메시지를 모든 클라이언트에게 전송
        for (WebSocketSession webSocketSession : sessions) {
            try {
                webSocketSession.sendMessage(new TextMessage("Echo: " + message.getPayload()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
// WebSocket 에러 처리
        System.out.println("WebSocket 오류: " + exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
// 클라이언트 연결 종료 시 세션 제거
        sessions.remove(session);
        System.out.println("웹소켓 연결 종료: " + session.getId());
    }

    @Override
    public boolean supportsPartialMessages() {
        // 부분 메시지 지원 여부 (기본적으로 false)
        return false;
    }
}