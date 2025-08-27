package com.example.kinover_backend.config;

import com.example.kinover_backend.redis.ChatMessageSubscriber;
import com.example.kinover_backend.redis.UserStatusSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final ChatMessageSubscriber chatMessageSubscriber;
    private final UserStatusSubscriber userStatusSubscriber;

    @Bean
    public RedisMessageListenerContainer messageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 1) 고정 채널: 채팅 메시지
        container.addMessageListener(
                new MessageListenerAdapter(chatMessageSubscriber, "onMessage"),
                chatTopic()
        );

        // 2) 패턴 채널: 가족 상태(online/offline): family:status:<uuid>
        container.addMessageListener(
                new MessageListenerAdapter(userStatusSubscriber, "onMessage"),
                userStatusPatternTopic()
        );

        return container;
    }

    @Bean
    public ChannelTopic chatTopic() {
        return new ChannelTopic("chat:messages");
    }

    @Bean
    public PatternTopic userStatusPatternTopic() {
        // family:status:<uuid> 전부 매칭
        return new PatternTopic("family:status:*");
    }
}
