package com.example.kinover_backend.config;

import org.redisson.api.listener.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class ChatMessageListener implements MessageListener<String> {

    private static final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    // ✅ Spring Redis 용 메시지 수신 메서드
    public void handleMessage(String message) {
        System.out.println("Redis에서 받은 메시지 (Spring Redis): " + message);

        broadcast(message);
    }

    // ✅ Redisson 용 메시지 수신 메서드
    @Override
    public void onMessage(CharSequence channel, String message) {
        System.out.println("Redis에서 받은 메시지 (Redisson): " + message);

        broadcast(message);
    }

    // ✅ 실제 브로드캐스트 로직 (중복 제거)
    private void broadcast(String message) {
        for (WebSocketSession session : sessions) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void addSession(WebSocketSession session) {
        sessions.add(session);
    }

    public static void removeSession(WebSocketSession session) {
        sessions.remove(session);
    }
}
