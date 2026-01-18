package dev.cerios.maugame.mauengine.player;

import dev.cerios.maugame.mauengine.exception.GameException;
import dev.cerios.maugame.mauengine.exception.MauEngineBaseException;
import dev.cerios.maugame.mauengine.exception.NotSupportedOperation;
import dev.cerios.maugame.mauengine.game.GameEventListener;
import dev.cerios.maugame.mauengine.game.action.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.ListOrderedMap;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Consumer;

@Slf4j
public class PlayerRunningState implements PlayerStorage {

    private final AtomicInteger activeCounter;

    private final UUID gameId;
    private final ListOrderedMap<String, Player> players = new ListOrderedMap<>();
    private int nextIndex;
    private int scorePoints;
    private final List<String> playerRank = new LinkedList<>();
    private final Deque<Player> winCandidates = new LinkedList<>();
    private final Consumer<Collection<Player>> stateSwitcher;

    private final Random random;
    private final ScheduledExecutorService executor;
    private final Map<String, FutureWithTimeout> futures = new HashMap<>();

    private final long turnTimeoutMs;

    @Getter
    private final ActionPublisher actionPublisher;

    private final ReadWriteLock globalLock;

    private final Map<String, Integer> scores;

    private final List<Consumer<Player>> timeoutListeners = new LinkedList<>();
    private final Consumer<NpcPlayer> npcTurnListener;

    PlayerRunningState(
        UUID gameId,
        Random random,
        Collection<Player> playerCollection,
        Map<String, Integer> scores,
        long turnTimeoutMs,
        Consumer<Collection<Player>> stateSwitcher,
        ReadWriteLock globalLock,
        ActionPublisherBuilder builder,
        Consumer<NpcPlayer> npcTurnListener
    ) {
        this(
            gameId,
            random,
            playerCollection,
            scores,
            turnTimeoutMs,
            stateSwitcher,
            globalLock,
            builder,
            Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory()),
            npcTurnListener
        );
    }

    PlayerRunningState(
        UUID gameId,
        Random random,
        Collection<Player> playerCollection,
        Map<String, Integer> scores,
        long turnTimeoutMs,
        Consumer<Collection<Player>> stateSwitcher,
        ReadWriteLock globalLock,
        ActionPublisherBuilder builder,
        ScheduledExecutorService executor,
        Consumer<NpcPlayer> npcTurnListener
    ) {
        this.gameId = gameId;
        this.random = random;
        this.actionPublisher = builder.withPlayers(players::valueList).build();
        this.turnTimeoutMs = turnTimeoutMs;
        this.stateSwitcher = stateSwitcher;
        this.globalLock = globalLock;
        this.scores = scores;
        this.scorePoints = playerCollection.size() - 1;

        this.players.putAll(
            playerCollection.stream()
                .collect(LinkedHashMap::new, (map, p) -> map.put(p.getPlayerId(), p), LinkedHashMap::putAll)
        );
        this.activeCounter = new AtomicInteger(this.players.size());
        this.executor = executor;
        this.npcTurnListener = npcTurnListener;
    }

    record FutureWithTimeout(Future<?> future, long expireAtMs) {
        void cancel() {
            future.cancel(true);
        }
    }

    @Override
    public List<Player> getPlayers() {
        return players.valueList();
    }

    public Player getCurrentPlayer() {
        return players.getValue((nextIndex - 1) % players.size());
    }

    @Override
    public Player registerPlayer(String username, GameEventListener eventListener) throws GameException {
        throw new NotSupportedOperation("Registering player with username is not supported at this state.");
    }

    @Override
    public void removePlayer(String playerId) {
        throw new NotSupportedOperation("Removing player with username is not supported at this state.");
    }

    @FunctionalInterface
    public interface BiFunctionChecked<T, U, R> {
        R apply(T t, U u) throws MauEngineBaseException;
    }

    public void getPlayerForPlay(String playerId, BiFunctionChecked<ActionPublisher, Player, Boolean> playerFunction) throws MauEngineBaseException {
        var player = getPlayer(playerId);
        if (!player.getPlayerId().equals(getCurrentPlayer().getPlayerId())) {
            throw new GameException("It's not a player's turn.");
        }
        poke(playerId);

        var shouldWin = playerFunction.apply(actionPublisher, player);

        if (shouldWin)
            winCandidates.add(player);

        shiftPlayer();
    }

    @Override
    public Player getPlayer(String playerId) throws GameException {
        var player = players.get(playerId);
        if (player == null) {
            throw new GameException("No player with id `" + playerId + "` was found.");
        }
        return player;
    }

    public void listenTimeout(Consumer<Player> listener) {
        timeoutListeners.add(listener);
    }

    /**
     *
     * @return whether game should continue
     */
    public boolean approveWinCandidates() {
        while (!winCandidates.isEmpty()) {
            win(winCandidates.remove());
        }
        return activeCounter.get() > 1;
    }

    public void endInstantly() {
        players.valueList().stream()
            .filter(p -> !p.isFinished())
            .forEach(p -> {
                playerRank.add(p.getUsername());
                addScore(p.getUsername());
                poke(p.getPlayerId());
                p.deactivate();
            });
        activeCounter.set(0);
        actionPublisher.publishActionToAll(new EndAction(getPlayerRank(), provideScoresCopy()));
        stateSwitcher.accept(getPlayers());
    }

    /**
     * Make player a winner and deactivates him.
     *
     * @param player to transform to winner
     * @return whether game should continue
     */
    private boolean win(Player player) {
        player.deactivate();
        player.getHand().clear();
        activeCounter.decrementAndGet();
        playerRank.add(player.getUsername());
        addScore(player.getUsername());
        Optional.ofNullable(futures.remove(player.getPlayerId())).ifPresent(FutureWithTimeout::cancel);

        var gameContinues = activeCounter.get() > 1;
        if (gameContinues)
            actionPublisher.publishActionToAll(new SendRankAction(getPlayerRank()));
        else {
            if (activeCounter.get() == 1) {
                loseLastActivePlayer();
            }
            actionPublisher.publishActionToAll(new EndAction(getPlayerRank(), provideScoresCopy()));
            stateSwitcher.accept(getPlayers());
        }
        return gameContinues;
    }

    private Map<String, Integer> provideScoresCopy() {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(scores.entrySet());
        list.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return Collections.unmodifiableMap(result);
    }

    private void addScore(String username) {
        int nextScore = provideNextScore();
        scores.compute(username, (_, v) -> v == null ? nextScore : v + nextScore);
    }

    private int provideNextScore() {
        if (scorePoints > 0) {
            return scorePoints--;
        }
        return 0;
    }

    void initializePlayer() {
        nextIndex = random.nextInt(players.size());
        shiftPlayer();
    }

    private void shiftPlayer() {
        var currentPlayer = findNextPlayer();
        var expireTime = System.currentTimeMillis() + turnTimeoutMs;
        var timeoutFuture = executor.schedule(() -> timeoutPlayer(currentPlayer), turnTimeoutMs, TimeUnit.MILLISECONDS);
        var previousFuture = futures.put(currentPlayer.getPlayerId(), new FutureWithTimeout(timeoutFuture, expireTime));
        if (previousFuture != null) previousFuture.cancel();
        actionPublisher.publishActionToAll(new PlayerShiftAction(currentPlayer, expireTime));

        log.debug("{}: {}'s turn", gameId, currentPlayer.getUsername());

        var firstCandidate = winCandidates.peek();
        if (firstCandidate != null && firstCandidate.getPlayerId().equals(currentPlayer.getPlayerId())) {
            winCandidates.removeFirst();
        }

        if (currentPlayer instanceof NpcPlayer npc) {
            npcTurnListener.accept(npc);
        }
    }

    private void timeoutPlayer(Player player) {
        log.debug("{}: {} timed out", gameId, player.getUsername());
        final var l = globalLock.writeLock();
        try {
            l.lock();
            var activeCount = activeCounter.decrementAndGet();
            players.remove(player.getPlayerId());
            actionPublisher.publishAction(player, new DisqualifiedAction());
            actionPublisher.publishActionToAll(new RemovePlayerAction(player));
            if (activeCount == 1) {
                win(findNextPlayer());
            } else {
                shiftPlayer();
            }
            for (var listener : timeoutListeners) {
                listener.accept(player);
            }
        } finally {
            l.unlock();
        }
    }

    private void poke(String playerId) {
        Optional.ofNullable(futures.remove(playerId)).ifPresent(FutureWithTimeout::cancel);
    }

    /**
     * retrieves last turn expire time if present, or {@code -1}
     *
     * @param playerId
     * @return last expire time if possible
     */
    public long getLastExpire(String playerId) {
        return Optional.ofNullable(futures.get(playerId))
            .map(FutureWithTimeout::expireAtMs)
            .orElse(-1L);
    }

    public List<String> getPlayerRank() {
        return List.copyOf(playerRank);
    }

    private Player findNextPlayer() {
        if (activeCounter.get() < 1)
            throw new RuntimeException("There is no next player");

        Player nextPlayer;
        do {
            nextPlayer = players.getValue(nextIndex++ % players.size());
        } while (nextPlayer.isFinished());
        return nextPlayer;
    }

    private void loseLastActivePlayer() {
        var losingPlayer = findNextPlayer();
        losingPlayer.getHand().clear();
        losingPlayer.deactivate();
        Optional.ofNullable(futures.remove(losingPlayer.getPlayerId())).ifPresent(FutureWithTimeout::cancel);
        activeCounter.decrementAndGet();
        playerRank.add(losingPlayer.getUsername());
        addScore(losingPlayer.getUsername());
    }
}