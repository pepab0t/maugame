package dev.cerios.maugame.websocket.ratelimit;

import dev.cerios.maugame.websocket.event.DisconnectEvent;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final int tokenCapacity;
    private final Duration refillEvery;

    public RateLimiter(
        @Value("${maugame.rate-limiter.tokens:20}") int tokenCapacity,
        @Value("${maugame.rate-limiter.reset-every-seconds:3}") int refillEverySeconds
    ) {
        this.tokenCapacity = tokenCapacity;
        this.refillEvery = Duration.ofSeconds(refillEverySeconds);
    }


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
