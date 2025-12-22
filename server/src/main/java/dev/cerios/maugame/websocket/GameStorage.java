package dev.cerios.maugame.websocket;

import dev.cerios.maugame.mauengine.exception.GameException;
import dev.cerios.maugame.mauengine.game.Game;
import dev.cerios.maugame.mauengine.game.GameFactory;
import dev.cerios.maugame.mauengine.game.GamePlayer;
import dev.cerios.maugame.websocket.exception.LobbyAlreadyExistsException;
import dev.cerios.maugame.websocket.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
@RequiredArgsConstructor
@Slf4j
public class GameStorage {

    private final GameFactory gameFactory;
    private final MessageDistributor distributor;
    private final PlayerSessionStorage storage;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final LinkedHashMap<UUID, NamedGame> publicGames = new LinkedHashMap<>();
    private final HashMap<UUID, NamedGame> privateGames = new HashMap<>();
    private final HashMap<String, UUID> gameRefs = new HashMap<>();
    private final MauSettings mauSettings;

    public GamePlayer registerToRandom(String username) {
        return runLocked(
            lock.writeLock(), () -> resolveRandomGame(game -> {
                log.debug("found game {} for user {}", game.getId(), username);
                var player = game.registerPlayer(username, distributor::distribute);
                storage.registerGame(player.getPlayerId(), game);
                log.info("{} registered to random game {}", player, game.getId());
                return player;
            })
        );
    }

    public GamePlayer registerToNamed(String username, String gameName) throws NotFoundException, GameException {
        var ng = runLocked(
            lock.readLock(), () -> {
                var gameId = gameRefs.get(gameName);
                if (gameId == null) {
                    throw new NotFoundException("Game `" + gameName + "` not found");
                }
                var namedGame = publicGames.get(gameId);
                if (namedGame == null) {
                    namedGame = privateGames.get(gameId);
                }
                return namedGame;
            }
        );

        var player = ng.game().registerPlayer(username, distributor::distribute);
        storage.registerGame(player.getPlayerId(), ng.game());
        return player;
    }

    public GamePlayer registerToNew(String username, String gameName, boolean isPrivate) throws LobbyAlreadyExistsException {
        var newGame = gameFactory.createGame(mauSettings);
        runLocked(
            lock.writeLock(), () -> {
                if (gameRefs.containsKey(gameName)) {
                    throw new LobbyAlreadyExistsException(gameName);
                }
                gameRefs.put(gameName, newGame.getId());
                (isPrivate ? privateGames : publicGames)
                    .put(newGame.getId(), new NamedGame(gameName, newGame));
                return null;
            }
        );

        newGame.listenStart(this::remove);

        try {
            var player = newGame.registerPlayer(username, distributor::distribute);
            storage.registerGame(player.getPlayerId(), newGame);
            return player;
        } catch (GameException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T resolveRandomGame(GameHandlerFunction<T> gameHandler) {
        var iterator = publicGames.values().iterator();

        while (true) {
            Game game = iterator.hasNext() ? iterator.next().game() : createAndStorePublicGame();
            try {
                T out = gameHandler.handle(game);
                if (game.getFreeCapacity() == 0) iterator.remove();
                return out;
            } catch (GameException e) {
                log.debug("error handle game {}: ", game.getId(), e);
            }
        }
    }

    private Game createAndStorePublicGame() {
        var g = gameFactory.createGame(mauSettings);
        publicGames.putLast(g.getId(), new NamedGame(g));
        g.listenStart(this::remove);
        return g;
    }

    public void remove(UUID gameId) {
        runLocked(
            lock.writeLock(), () -> {
                var namedGame = publicGames.remove(gameId);
                if (namedGame == null)
                    namedGame = privateGames.remove(gameId);
                if (namedGame != null)
                    gameRefs.remove(namedGame.name());
            }
        );
    }

    public void clear() {
        runLocked(
            lock.writeLock(), () -> {
                publicGames.clear();
                privateGames.clear();
                gameRefs.clear();
            }
        );
    }

    private void runLocked(Lock internalLock, Runnable runnable) {
        try {
            internalLock.lock();
            runnable.run();
        } finally {
            internalLock.unlock();
        }
    }

    private <R, E extends Throwable> R runLocked(Lock internalLock, CheckedTask<R, E> task) throws E {
        try {
            internalLock.lock();
            return task.run();
        } finally {
            internalLock.unlock();
        }
    }

    private record NamedGame(String name, Game game) {
        private NamedGame(Game game) {
            this(null, game);
        }
    }

    interface GameHandlerFunction<T> {
        T handle(Game game) throws GameException;
    }

    interface CheckedTask<R, E extends Throwable> {
        R run() throws E;
    }
}
