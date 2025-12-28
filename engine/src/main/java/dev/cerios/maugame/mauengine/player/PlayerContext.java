package dev.cerios.maugame.mauengine.player;

import dev.cerios.maugame.mauengine.exception.GameException;
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
    private Consumer<NpcPlayer> npcListener;

    public PlayerContext(PlayerStateFactory factory) {
        this.factory = factory;
    }

    public void listenPlayerTimeout(Consumer<Player> listener) {
        timeoutListener = listener;
    }

    public void listenStartGame(Consumer<UUID> startListener) {
        this.startListeners.add(startListener);
    }

    public void listenNpcTurn(Consumer<NpcPlayer> npcListener) {
        this.npcListener = npcListener;
    }

    public PlayerLobbyState getLobby() throws GameException {
        if (players instanceof PlayerLobbyState)
            return (PlayerLobbyState) players;
        throw new GameException("Not in lobby state.");
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
            var shuffledPlayers = new ArrayList<>(playerCollection);
            Collections.shuffle(shuffledPlayers);
            var state = factory.createRunningState(shuffledPlayers, scores, this::setFinishState, npcListener);
            if (timeoutListener != null)
                state.listenTimeout(timeoutListener);
            players = state;
            logState();
            state.initializePlayer();
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
