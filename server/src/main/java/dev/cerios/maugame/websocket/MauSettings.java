package dev.cerios.maugame.websocket;

import dev.cerios.maugame.mauengine.game.GameSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("maugame")
@Data
public class MauSettings implements GameSettings {
    @Min(2)
    @Max(2)
    private volatile int minPlayers = 2;
    @Min(2)
    @Max(5)
    private volatile int maxPlayers = 5;

    @Min(10)
    private volatile long turnTimeoutMs = 10_000;
}
