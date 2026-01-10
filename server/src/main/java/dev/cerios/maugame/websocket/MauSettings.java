package dev.cerios.maugame.websocket;

import dev.cerios.maugame.mauengine.game.GameSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("maugame")
@Data
public class MauSettings implements GameSettings {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 5;
    public static final long TURN_TIMEOUT_MS = 60_000;
    public static final boolean CHEATING_ENABLED = false;
    public static final int MAX_CHAT_SIZE = 10;

    @Min(2)
    @Max(2)
    private volatile int minPlayers = MIN_PLAYERS;
    @Min(2)
    @Max(5)
    private volatile int maxPlayers = MAX_PLAYERS;

    @Min(10)
    private volatile long turnTimeoutMs = TURN_TIMEOUT_MS;

    private volatile boolean cheatingEnabled = CHEATING_ENABLED;

    @Min(0)
    @Max(1000)
    private volatile int maxChatSize = MAX_CHAT_SIZE;

    public void restoreDefaults() {
        minPlayers = MIN_PLAYERS;
        maxPlayers = MAX_PLAYERS;
        turnTimeoutMs = TURN_TIMEOUT_MS;
        cheatingEnabled = CHEATING_ENABLED;
        maxChatSize = MAX_CHAT_SIZE;
    }
}
