package com.devboard.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Contador de janela fixa em memória (Caffeine), suficiente para o MVP sem fila/cache externos.
 * A janela de cada chave é fixada na criação da entrada e não se renova a cada acesso.
 */
@Component
public class RateLimiterService {

    private final Cache<String, Counter> counters = Caffeine.newBuilder()
            .expireAfter(new Expiry<String, Counter>() {
                @Override
                public long expireAfterCreate(String key, Counter value, long currentTime) {
                    return TimeUnit.SECONDS.toNanos(value.windowSeconds);
                }

                @Override
                public long expireAfterUpdate(String key, Counter value, long currentTime, long currentDuration) {
                    return currentDuration;
                }

                @Override
                public long expireAfterRead(String key, Counter value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    public boolean tryConsume(String key, int limit, long windowSeconds) {
        Counter counter = counters.get(key, k -> new Counter(windowSeconds));
        return counter.count.incrementAndGet() <= limit;
    }

    public long remainingSeconds(String key) {
        return counters.policy().expireVariably()
                .map(policy -> policy.getExpiresAfter(key, TimeUnit.SECONDS))
                .filter(OptionalLong::isPresent)
                .map(OptionalLong::getAsLong)
                .map(seconds -> Math.max(1, seconds))
                .orElse(1L);
    }

    private static final class Counter {
        private final AtomicInteger count = new AtomicInteger(0);
        private final long windowSeconds;

        private Counter(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
