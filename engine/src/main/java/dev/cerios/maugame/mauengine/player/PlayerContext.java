package dev.cerios.maugame.mauengine.player;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Consumer;

@Slf4j
public class PlayerContext {
    private final PlayerStateFactory factory;

    @Getter
    private PlayerStorage players;
    private Map<String, Integer> scores = new HashMap<>();
    private Consumer<Player> timeoutListener;
    private final List<Consumer<UUID>> startListeners = new LinkedList<>();

    public PlayerContext(PlayerStateFactory factory) {
        this.factory = factory;
    }

    public void listenPlayerTimeout(Consumer<Player> listener) {
        timeoutListener = listener;
    }

    public void listenStartGame(Consumer<UUID> startListener) {
        this.startListeners.add(startListener);
    }

    public void setLobbyState() {
        var state = factory.createLobbyState(this::setRunningState);
        state.listenStart(startListeners);
        if (startListeners.isEmpty()) throw new RuntimeException("Wrong setup, no start listener.");
        players = state;
        logState();
    }

    public void setRunningState(Collection<Player> playerCollection) {
        if (players instanceof PlayerReadyStorage) {
            playerCollection.forEach(Player::activate);
            var state = factory.createRunningState(playerCollection, scores, this::setFinishState);
            if (timeoutListener != null)
                state.listenTimeout(timeoutListener);
            players = state;
            logState();
        } else {
            throw new RuntimeException(String.format("Invalid state `%s` for transition to running state.", players.getClass().getSimpleName()));
        }
    }

    public void setFinishState(Collection<Player> playerCollection) {
        if (players instanceof PlayerRunningState) {
            var finish = factory.createFinishState(playerCollection, this::setRunningState);
            finish.listenStart(startListeners);
            if (startListeners.isEmpty()) throw new RuntimeException("Wrong setup, no start listener.");
            players = finish;
            logState();
        } else {
            throw new RuntimeException(String.format("Invalid state `%s` for transition to finish state.", players.getClass().getSimpleName()));
        }
    }

    private void logState() {
        log.info("Game {} switched to state: {}", factory.getGameId(), players.getClass().getSimpleName());
    }
}
