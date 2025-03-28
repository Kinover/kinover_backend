package com.example.kinover_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class WebSocketController {

    private final StringRedisTemplate redisTemplate;
    private final ChannelTopic topic;

    // 채팅 메시지 전송 (Redis Publish)
    @PostMapping("/send")
    public void sendMessage(@RequestParam String message) {
        // convertAndSend()는 Redis에 **메시지를 발행(Publish)**하는 역할을 합니다.
        redisTemplate.convertAndSend(topic.getTopic(), message);
        System.out.println("Redis로 메시지 발행: " + message);
    }
}
