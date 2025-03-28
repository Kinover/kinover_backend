package com.example.kinover_backend.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;

import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.core.StringRedisTemplate;

// Redis의 설정을 관리하고, 메시지 리스너와 Redis 채널을 연결합니다.

@Configuration
public class RedisConfig {

    // Redis에서 메시지 듣는 역할(귀 기울이는 애)
    @Bean
    public RedisMessageListenerContainer messageListenerContainer(StringRedisTemplate redisTemplate) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisTemplate.getConnectionFactory());
        container.addMessageListener(messageListener(), topic());
        return container;
    }



    // Redis에서 받은 메시지를 ChatMessageListener 클래스가 처리하게 연결
    @Bean
    public MessageListener messageListener() {
        return new MessageListenerAdapter(new ChatMessageListener());
    }

    // Redis에서 **chat:messages**라는 채널을 듣겠다!
    @Bean
    public ChannelTopic topic() {
        return new ChannelTopic("chat:messages");  // 채팅 메시지를 발행할 채널 이름
    }
}
