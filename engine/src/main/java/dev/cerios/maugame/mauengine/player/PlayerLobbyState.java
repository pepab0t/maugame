package dev.cerios.maugame.mauengine.player;

import dev.cerios.maugame.mauengine.exception.GameException;
import dev.cerios.maugame.mauengine.game.GameEventListener;
import dev.cerios.maugame.mauengine.game.action.*;
import dev.cerios.maugame.mauengine.player.store.PlayerStore;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.iterators.LoopingIterator;

import java.util.*;
import java.util.function.Consumer;

@Slf4j
public class PlayerLobbyState extends PlayerReadyStorage {
    private static final Set<String> TAKEN_NAMES = Set.of("Bayraktar", "Baykar", "Baklajuan", "Babakar", "Brumbalek");
    private final Iterator<String> NPC_NAMES = new LoopingIterator<>(TAKEN_NAMES);

    private final PlayerStore store;

    @Getter // TODO remove
    private final Map<String, Ready> readyStates = new HashMap<>();
    private final List<Consumer<UUID>> startListeners = new LinkedList<>();
    private Player leader;

    private final int minPlayers;
    private final int maxPlayers;
    private final UUID gameId;
    @Getter
    private final ActionPublisher actionPublisher;
    private final Consumer<Collection<Player>> stateSwitcher;

    PlayerLobbyState(
        PlayerStore store,
        UUID gameId,
        Consumer<Collection<Player>> stateSwitcher,
        ActionPublisherBuilder publisherBuilder
    ) {
        this(store, 2, 5, gameId, stateSwitcher, publisherBuilder);
    }

    PlayerLobbyState(
        PlayerStore store,
        int minPlayers,
        int maxPlayers,
        UUID gameId,
        Consumer<Collection<Player>> stateSwitcher,
        ActionPublisherBuilder builder
    ) {
        this.store = store;
        store.getUpdates().consume(this::handlePlayerChange);
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.actionPublisher = builder.withPlayers(store::getAll).build(); // TODO get rid of supplier
        this.gameId = gameId;
        this.stateSwitcher = stateSwitcher;
    }

    private void handlePlayerChange(PlayerStore.PlayerChange playerChange) {
        var player = playerChange.player();
        switch (playerChange.changeType()) {
            case INSERT -> {
                if (player instanceof NpcPlayer) {
                    return;
                }
                var playerId = player.getPlayerId();
                readyStates.put(playerId, new Ready(player));

                actionPublisher.publishActionExcludingPlayer(
                    new RegisterAction(gameId, player, false),
                    playerId
                );
                actionPublisher.publishAction(player, new RegisterAction(gameId, player, true));
                actionPublisher.publishAction(player, new PlayersAction(List.copyOf(store.getAll())));
                if (leader == null) {
                    leader = player;
                    actionPublisher.publishActionToAll(new LeaderAction(leader.getUsername()));
                } else {
                    actionPublisher.publishAction(player, new LeaderAction(leader.getUsername()));
                }
                for (var r : readyStates.values()) {
                    if (r.set(false))
                        actionPublisher.publishActionExcludingPlayer(
                            new UnreadyAction(r.getPlayer().getUsername()),
                            playerId
                        );
                }
                for (var p : store.getAll()) {
                    if (p instanceof NpcPlayer npc)
                        actionPublisher.publishAction(player, new ReadyAction(npc.getUsername()));
                }
            }
            case DELETE -> {
                var nonNpcs = findNonNpcPlayers();
                if (nonNpcs.isEmpty()) {
                    store.clear();
                    readyStates.clear();
                    leader = null;
                    return;
                } else if (leader == player) {
                    leader = nonNpcs.getFirst();
                    actionPublisher.publishActionToAll(new LeaderAction(leader.getUsername()));
                }

                for (var ready : readyStates.values()) {
                    if (ready.set(false))
                        actionPublisher.publishActionToAll(new UnreadyAction(ready.getPlayer().getUsername()));
                }
                actionPublisher.publishActionToAll(new RemovePlayerAction(player, 0));
            }
        }
    }

    @Override
    public Player registerPlayer(String username, GameEventListener eventListener) throws GameException {
        if (store.containsUsername(username))
            throw new GameException("Username `" + username + "` is given");
        if (store.count() >= maxPlayers) {
            throw new GameException("Too many players");
        }
        var player = PlayerFactory.createPlayer(username, eventListener);
        store.addPlayer(player);
        return player;
    }

    public boolean isLeader(String playerId) {
        return leader != null && leader.getPlayerId().equals(playerId);
    }

    public void registerNpcPlayer() throws GameException {
        if (store.count() >= maxPlayers) {
            throw new GameException("Too many players");
        }
        var username = NPC_NAMES.next();
        if (store.containsUsername(username))
            throw new GameException("Username `" + username + "` is given");

        var npc = PlayerFactory.createNpcPlayer(username);
        var playerId = npc.getPlayerId();

        store.addPlayer(npc);
        readyStates.put(playerId, new NpcReady(npc));

        actionPublisher.publishActionToAll(new RegisterAction(gameId, npc, false));
        for (var r : readyStates.values()) {
            if (r.set(false)) actionPublisher.publishActionToAll(new UnreadyAction(r.getPlayer().getUsername()));
        }
        actionPublisher.publishActionToAll(new ReadyAction(npc.getUsername()));
    }

    @Override
    public void removePlayer(String playerId) {
        store.deleteById(playerId)
            .ifPresentOrElse(
                player -> log.info("(Game {}) Removed player {}", gameId, player),
                () -> log.info("(Game {}) No player with id `{}` was found", gameId, playerId)
            );
    }

    public void removePlayerByUsername(String username) {
        store.deleteByUsername(username)
            .ifPresentOrElse(
                player -> player.trigger(new DisqualifiedAction("Your username is taken by registered player.")),
                () -> log.info("(Game {}) Player with username `{}` is not in the game.", gameId, username)
            );
    }

    public void kickPlayer(String leaderId, String username) throws GameException {
        if (!isLeader(leaderId)) {
            throw new GameException("Player is not leader of lobby %s.".formatted(gameId));
        }
        var playerKick = store.deleteByUsername(
                username,
                player -> {
                    if (player.getPlayerId().equals(leaderId)) {
                        throw new GameException("Cannot kick leader.");
                    }
                }
            )
            .orElseThrow(() -> new GameException("Username `" + username + "` is not found."));
        actionPublisher.publishAction(playerKick, new DisqualifiedAction());
    }

    private List<Player> findNonNpcPlayers() {
        return store.getFiltered(p -> !(p instanceof NpcPlayer));
    }

    @Override
    public Player getPlayer(String playerId) throws GameException {
        return store.getById(playerId)
            .orElseThrow(() -> new GameException("Player `" + playerId + "` not found"));
    }

    @Override
    public List<Player> getPlayers() {
        return store.getAll().stream().toList();
    }

    @Override
    public void setReady(String playerId) throws GameException {
        var ready = readyStates.get(playerId);
        if (ready == null)
            throw new GameException("Player `" + playerId + "` not found");

        if (!ready.set(true)) {
            log.trace("game {}: player `{}` ready status true not changed", gameId, playerId);
            return;
        }

        actionPublisher.publishActionToAll(new ReadyAction(ready.getPlayer().getUsername()));

        log.debug("{}: {} ready", gameId, ready.getPlayer());

        // at least one is not ready
        if (!hasEnoughPlayers() || readyStates.values().stream().anyMatch(r -> !r.get()))
            return;

        log.debug("game {} ready to start", gameId);

        triggerStart();
        stateSwitcher.accept(getPlayers());
    }

    @Override
    public void setUnready(String playerId) throws GameException {
        var ready = getPlayerReady(playerId);
        if (!ready.set(false)) {
            log.trace("game {}: players {} ready status false not changed", gameId, playerId);
            return;
        }
        actionPublisher.publishActionToAll(new UnreadyAction(ready.getPlayer().getUsername()));
    }

    public void listenStart(List<Consumer<UUID>> listeners) {
        startListeners.addAll(listeners);
    }

    private void triggerStart() {
        for (var listener : startListeners) {
            listener.accept(gameId);
        }
    }

    public int getFreeCapacity() {
        return maxPlayers - store.count();
    }

    private boolean hasEnoughPlayers() {
        return store.count() >= minPlayers;
    }
}
