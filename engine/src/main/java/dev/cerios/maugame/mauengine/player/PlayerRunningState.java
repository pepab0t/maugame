package dev.cerios.maugame.mauengine.player;

import dev.cerios.maugame.mauengine.exception.GameException;
import dev.cerios.maugame.mauengine.exception.MauEngineBaseException;
import dev.cerios.maugame.mauengine.exception.NotSupportedOperation;
import dev.cerios.maugame.mauengine.game.GameEventListener;
import dev.cerios.maugame.mauengine.game.action.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.ListOrderedMap;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Consumer;

@Slf4j
public class PlayerRunningState implements PlayerStorage {

    private final AtomicInteger activeCounter;

    private final UUID gameId;
    private final ListOrderedMap<String, PlayerWrapper> players = new ListOrderedMap<>();
    private int nextIndex;
    private int scorePoints;
    private final List<String> playerRank = new LinkedList<>();
    private final Deque<PlayerWrapper> winCandidates = new LinkedList<>();
    private final Consumer<Collection<Player>> stateSwitcher;

    private final Random random;
    private final ScheduledExecutorService executor;

    private final long turnTimeoutMs;

    @Getter
    private final ActionPublisher actionPublisher;

    private final ReadWriteLock globalLock;

    private final Map<String, Integer> scores;

    private final List<Consumer<Player>> disqualifyListeners = new LinkedList<>();
    private final List<Consumer<Player>> turnTimeoutListeners = new LinkedList<>();
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
        this.actionPublisher = builder.withPlayers(this::getPlayers).build();
        this.turnTimeoutMs = turnTimeoutMs;
        this.stateSwitcher = stateSwitcher;
        this.globalLock = globalLock;
        this.scores = scores;
        this.scorePoints = playerCollection.size() - 1;

        this.players.putAll(
            playerCollection.stream()
                .collect(LinkedHashMap::new, (map, p) -> map.put(p.getPlayerId(), new PlayerWrapper(p)), LinkedHashMap::putAll)
        );
        this.activeCounter = new AtomicInteger(this.players.size());
        this.executor = executor;
        this.npcTurnListener = npcTurnListener;
    }

    @Override
    public List<Player> getPlayers() {
        return players.valueList().stream().map(PlayerWrapper::getPlayer).toList();
    }

    public Player getCurrentPlayer() {
        return players.getValue((nextIndex - 1) % players.size()).getPlayer();
    }

    @Override
    public Player registerPlayer(String username, GameEventListener eventListener) {
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

    public void getPlayerForPlayWithoutPoke(String playerId, BiFunctionChecked<ActionPublisher, Player, Boolean> playerFunction) throws MauEngineBaseException {
        getPlayerForPlay(playerId, playerFunction, null);
    }

    public void getPlayerForPlay(String playerId, BiFunctionChecked<ActionPublisher, Player, Boolean> playerFunction) throws MauEngineBaseException {
        getPlayerForPlay(playerId, playerFunction, this::poke);
    }

    private void getPlayerForPlay(
        String playerId,
        BiFunctionChecked<ActionPublisher, Player, Boolean> playerFunction,
        Consumer<String> playerIdConsumer
    ) throws MauEngineBaseException {
        if (playerIdConsumer != null) poke(playerId);
        var playerWrapper = getPlayerWrapper(playerId);
        var player = playerWrapper.getPlayer();
        if (!player.getPlayerId().equals(getCurrentPlayer().getPlayerId())) {
            throw new GameException("It's not a %s's turn.".formatted(player.getUsername()));
        }

        var shouldWin = playerFunction.apply(actionPublisher, player);

        if (shouldWin)
            winCandidates.add(playerWrapper);

        shiftPlayer();
    }

    private PlayerWrapper getPlayerWrapper(String playerId) throws GameException {
        var wrapper = players.get(playerId);
        if (wrapper == null) {
            throw new GameException("No player with id `" + playerId + "` was found.");
        }
        return wrapper;
    }

    @Override
    public Player getPlayer(String playerId) throws GameException {
        return getPlayerWrapper(playerId).getPlayer();
    }

    public void listenDisqualify(Consumer<Player> listener) {
        disqualifyListeners.add(listener);
    }

    public void listenTurnTimeout(Consumer<Player> listener) {
        turnTimeoutListeners.add(listener);
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
            .map(PlayerWrapper::getPlayer)
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
     * @param playerWrapper to transform to winner
     * @return whether game should continue
     */
    private boolean win(PlayerWrapper playerWrapper) {
        playerWrapper.getFuture().cancel();
        var player = playerWrapper.getPlayer();
        player.deactivate();
        player.getHand().clear();
        activeCounter.decrementAndGet();
        playerRank.add(player.getUsername());
        addScore(player.getUsername());

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
        var playerWrapper = findNextPlayer();
        var currentPlayer = playerWrapper.getPlayer();
        var expireTime = System.currentTimeMillis() + turnTimeoutMs;

        if (currentPlayer instanceof NpcPlayer npc) {
            executor.schedule(() -> npcTurnListener.accept(npc), 1, TimeUnit.SECONDS);
        } else {
            final Runnable timeoutRunnable = playerWrapper.getTimeouts() < 3
                ? () -> handleTimeout(playerWrapper)
                : () -> disqualifyPlayer(currentPlayer);

            var timeoutFuture = executor.schedule(timeoutRunnable, turnTimeoutMs, TimeUnit.MILLISECONDS);
            playerWrapper.getFuture().cancel();
            playerWrapper.setFuture(new FutureWithTimeout(timeoutFuture, expireTime));
        }
        actionPublisher.publishActionToAll(new PlayerShiftAction(currentPlayer, expireTime));

        log.debug("{}: {}'s turn", gameId, currentPlayer.getUsername());

        var firstCandidate = winCandidates.peek();
        if (firstCandidate != null && firstCandidate.getPlayer().getPlayerId().equals(currentPlayer.getPlayerId())) {
            winCandidates.removeFirst();
        }
    }

    private void handleTimeout(PlayerWrapper playerWrapper) {
        playerWrapper.increaseTimeoutCount();
        var player = playerWrapper.getPlayer();
        log.debug("({}) {}'s timeout number: {}", gameId, player.getUsername(), playerWrapper.getTimeouts());
        turnTimeoutListeners.forEach(l -> l.accept(playerWrapper.getPlayer()));
    }

    private void disqualifyPlayer(Player player) {
        log.debug("{}: {} timed out - disqualify", gameId, player.getUsername());
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
            for (var listener : disqualifyListeners) {
                listener.accept(player);
            }
        } finally {
            l.unlock();
        }
    }

    private void poke(String playerId) {
        Optional.ofNullable(players.get(playerId)).ifPresent(w -> {
            w.resetTimeouts();
            w.getFuture().cancel();
        });
    }

    /**
     * retrieves last turn expire time if present, or {@code -1}
     *
     * @param playerId
     * @return last expire time if possible
     */
    public long getLastExpire(String playerId) {
        return Optional.ofNullable(players.get(playerId))
            .map(w -> w.getFuture().expireAtMs())
            .orElse(PlayerWrapper.defaultFuture.expireAtMs());
    }

    public List<String> getPlayerRank() {
        return List.copyOf(playerRank);
    }

    private PlayerWrapper findNextPlayer() {
        if (activeCounter.get() < 1)
            throw new RuntimeException("There is no next player");

        PlayerWrapper nextPlayer;
        do {
            nextPlayer = players.getValue(nextIndex++ % players.size());
        } while (nextPlayer.getPlayer().isFinished());
        return nextPlayer;
    }

    private void loseLastActivePlayer() {
        var losingPlayerWrapper = findNextPlayer();
        losingPlayerWrapper.getFuture().cancel();
        var losingPlayer = losingPlayerWrapper.getPlayer();
        losingPlayer.getHand().clear();
        losingPlayer.deactivate();
        activeCounter.decrementAndGet();
        playerRank.add(losingPlayer.getUsername());
        addScore(losingPlayer.getUsername());
    }

    record FutureWithTimeout(Future<?> future, long expireAtMs) {
        void cancel() {
            future.cancel(true);
        }
    }

    @RequiredArgsConstructor
    static class PlayerWrapper {
        @Getter
        private final Player player;
        private int timeouts = 0;
        @Setter
        private volatile FutureWithTimeout future;

        private static final FutureWithTimeout defaultFuture = new FutureWithTimeout(CompletableFuture.completedFuture(null), -1);

        public synchronized void increaseTimeoutCount() {
            timeouts++;
        }

        public synchronized int getTimeouts() {
            return timeouts;
        }

        public synchronized void resetTimeouts() {
            timeouts = 0;
        }

        public synchronized FutureWithTimeout getFuture() {
            return future == null
                ? defaultFuture
                : future;
        }
    }
}