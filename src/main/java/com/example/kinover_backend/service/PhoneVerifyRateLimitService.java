package com.example.kinover_backend.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class PhoneVerifyRateLimitService {

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public boolean isAllowed(String key, int maxAttempts, long windowMillis) {
        long now = System.currentTimeMillis();
        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter(now));

        synchronized (counter) {
            if (now - counter.windowStart >= windowMillis) {
                counter.windowStart = now;
                counter.attempts.set(0);
            }

            int current = counter.attempts.incrementAndGet();
            return current <= maxAttempts;
        }
    }

    private static final class WindowCounter {
        private long windowStart;
        private final AtomicInteger attempts = new AtomicInteger(0);

        private WindowCounter(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
