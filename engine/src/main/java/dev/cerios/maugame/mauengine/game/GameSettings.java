package dev.cerios.maugame.mauengine.game;

public interface GameSettings {
    int getMinPlayers();

    int getMaxPlayers();

    long getTurnTimeoutMs();

    long getNpcIntervalMs();
}
