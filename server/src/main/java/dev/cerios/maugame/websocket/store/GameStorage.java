package dev.cerios.maugame.websocket.store;

import dev.cerios.maugame.mauengine.exception.GameException;
import dev.cerios.maugame.mauengine.game.Game;
import dev.cerios.maugame.mauengine.game.GameFactory;
import dev.cerios.maugame.mauengine.game.GamePlayer;
import dev.cerios.maugame.websocket.config.MauSettings;
import dev.cerios.maugame.websocket.exception.LobbyAlreadyExistsException;
import dev.cerios.maugame.websocket.exception.NotFoundException;
import dev.cerios.maugame.websocket.service.MessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static dev.cerios.maugame.mauengine.locking.LockUtils.runLocked;

@Component
@RequiredArgsConstructor
@Slf4j
public class GameStorage {

    private final GameFactory gameFactory;
    private final MessageSender distributor;
    private final PlayerStore storage;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final LinkedHashMap<UUID, NamedGame> publicGames = new LinkedHashMap<>();
    private final HashMap<UUID, NamedGame> privateGames = new HashMap<>();
    private final HashMap<String, UUID> gameRefs = new HashMap<>();
    private final MauSettings mauSettings;

    public GamePlayer registerToRandom(String username) {
        return runLocked(
            lock.writeLock(), () -> resolveRandomGame(game -> {
                log.debug("found game {} for user {}", game.getId(), username);
                var player = game.registerPlayer(username, distributor::enqueue);
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

        var player = ng.game().registerPlayer(username, distributor::enqueue);
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
            var player = newGame.registerPlayer(username, distributor::enqueue);
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
                log.debug("error in game {}: {}", game.getId(), e.getMessage());
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

    private interface GameHandlerFunction<T> {
        T handle(Game game) throws GameException;
    }

    private record NamedGame(String name, Game game) {
        private NamedGame(Game game) {
            this(null, game);
        }
    }
}
