package dev.cerios.maugame.mauengine.player;

import dev.cerios.maugame.mauengine.exception.GameException;

import java.util.Map;

@SuppressWarnings("ClassEscapesDefinedScope")
public abstract class PlayerReadyStorage implements PlayerStorage {
    public abstract void setReady(String playerId) throws GameException;

    public abstract void setUnready(String playerId) throws GameException;

    protected abstract Map<String, Ready> getReadyStates();

    protected final Ready getPlayerReady(String playerId) throws GameException {
        var ready = getReadyStates().get(playerId);
        if (ready == null) {
            throw new GameException("Player " + playerId + " not found.");
        }
        return ready;
    }
}
