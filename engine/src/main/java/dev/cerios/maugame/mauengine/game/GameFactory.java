package dev.cerios.maugame.mauengine.game;

import dev.cerios.maugame.mauengine.card.CardComparer;
import dev.cerios.maugame.mauengine.card.CardManager;
import dev.cerios.maugame.mauengine.player.PlayerContext;
import dev.cerios.maugame.mauengine.player.PlayerStateFactory;
import lombok.RequiredArgsConstructor;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static dev.cerios.maugame.mauengine.locking.LockUtils.wrapLock;

@RequiredArgsConstructor
public class GameFactory {
    private final Random random;

    public Game createGame(Random random, int minPlayers, int maxPlayers, long turnTimeoutMs) {
        var gameId = UUID.randomUUID();
        var globalLock = new ReentrantReadWriteLock(true);
        var playerStateFactory = new PlayerStateFactory(gameId, minPlayers, maxPlayers, random, globalLock, turnTimeoutMs);
        var playerContext = new PlayerContext(playerStateFactory);
        var cardManager = CardManager.create(random, new CardComparer());
        var core = new GameCore(cardManager, playerContext, gameId);
        var game = new Game(gameId, core, playerContext, globalLock);
        var autoPlayer = new AutoPlayHandler(cardManager, core, new Random());
        playerContext.setLobbyState();
        playerContext.listenNpcTurn(autoPlayer::computeAutoPlay);
        playerContext.listenTurnTimeout(wrapLock(globalLock.writeLock(), autoPlayer::computeAutoPlay));
        return game;
    }

    public Game createGame(int minPlayers, int maxPlayers, long turnTimeoutMs) {
        return createGame(random, minPlayers, maxPlayers, turnTimeoutMs);
    }

    public Game createGame(GameSettings settings) {
        return createGame(settings.getMinPlayers(), settings.getMaxPlayers(), settings.getTurnTimeoutMs());
    }
}
