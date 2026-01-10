package dev.cerios.maugame.websocket.ratelimit;

import dev.cerios.maugame.websocket.event.DisconnectEvent;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RateLimiter {

    private final Map<String, Bucket> sessionBuckets = new ConcurrentHashMap<>();
    private final int tokenCapacity = 20;
    private final Duration refillEvery = Duration.ofSeconds(3);

    public boolean canProceed(String sessionId) {
        return getBucket(sessionId).tryConsume(1);
    }

    @EventListener
    public void removeBucket(DisconnectEvent event) {
        Optional.ofNullable(sessionBuckets.remove(event.sessionId()))
            .ifPresent(_ -> log.info("Removed bucket for session {}", event.sessionId()));
    }

    private Bucket getBucket(String sessionId) {
        return sessionBuckets.computeIfAbsent(sessionId, _ -> createBucket());
    }

    private Bucket createBucket() {
        var bandwidth = Bandwidth.builder()
            .capacity(tokenCapacity)
            .refillGreedy(tokenCapacity, refillEvery)
            .build();
        return Bucket.builder()
            .addLimit(bandwidth)
            .build();
    }
}
