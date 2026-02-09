package dev.cerios.maugame.websocket.config;

import dev.cerios.maugame.mauengine.game.GameSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("maugame")
@Getter
@Setter
@ToString
public class MauSettings implements GameSettings {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 5;
    public static final long TURN_TIMEOUT_MS = 30_000;
    public static final boolean CHEATING_ENABLED = false;
    public static final int MAX_CHAT_SIZE = 10;

    @Min(2)
    @Max(2)
    private volatile int minPlayers;
    @Min(2)
    @Max(5)
    private volatile int maxPlayers;
    @Min(10)
    private volatile long turnTimeoutMs;
    private volatile boolean cheatingEnabled;
    @Min(0)
    @Max(1000)
    private volatile int maxChatSize;

    private final Integer tokenDurationSeconds;
    private final Integer refreshTokenDurationDays;

    public MauSettings(
        Integer tokenDurationSeconds,
        Integer refreshTokenDurationDays,
        int maxChatSize,
        boolean cheatingEnabled,
        long turnTimeoutMs,
        int maxPlayers
    ) {
        this.tokenDurationSeconds = tokenDurationSeconds;
        this.refreshTokenDurationDays = refreshTokenDurationDays;
        this.maxChatSize = maxChatSize;
        this.cheatingEnabled = cheatingEnabled;
        this.turnTimeoutMs = turnTimeoutMs;
        this.maxPlayers = maxPlayers;
        this.minPlayers = MIN_PLAYERS;
    }

    public void restoreDefaults() {
        minPlayers = MIN_PLAYERS;
        maxPlayers = MAX_PLAYERS;
        turnTimeoutMs = TURN_TIMEOUT_MS;
        cheatingEnabled = CHEATING_ENABLED;
        maxChatSize = MAX_CHAT_SIZE;
    }
}
