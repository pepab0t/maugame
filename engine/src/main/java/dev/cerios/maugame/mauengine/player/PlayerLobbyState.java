package dev.cerios.maugame.mauengine.player;

import dev.cerios.maugame.mauengine.exception.GameException;
import dev.cerios.maugame.mauengine.game.GameEventListener;
import dev.cerios.maugame.mauengine.game.action.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.iterators.LoopingIterator;
import org.apache.commons.collections4.map.ListOrderedMap;

import java.util.*;
import java.util.function.Consumer;

@Slf4j
public class PlayerLobbyState extends PlayerReadyStorage {
    private static final Set<String> TAKEN_NAMES = Set.of("Bayraktar", "Baykar", "Baklajuan", "Babakar", "Brumbalek");
    private final Iterator<String> NPC_NAMES = new LoopingIterator<>(TAKEN_NAMES);

    private final ListOrderedMap<String, Player> players = new ListOrderedMap<>();
    private final Map<String, Player> usernames = new HashMap<>();
    @Getter
    private final Map<String, Ready> readyStates = new HashMap<>();
    private final List<Consumer<UUID>> startListeners = new LinkedList<>();
    private Player leader;

    private final int minPlayers;
    private final int maxPlayers;
    private final UUID gameId;
    @Getter
    private final ActionPublisher actionPublisher;
    private final Consumer<Collection<Player>> stateSwitcher;

    PlayerLobbyState(UUID gameId, Consumer<Collection<Player>> stateSwitcher, ActionPublisherBuilder publisherBuilder) {
        this(2, 5, gameId, stateSwitcher, publisherBuilder);
    }

    PlayerLobbyState(
        int minPlayers,
        int maxPlayers,
        UUID gameId,
        Consumer<Collection<Player>> stateSwitcher,
        ActionPublisherBuilder builder
    ) {
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.actionPublisher = builder.withPlayers(players::valueList).build();
        this.gameId = gameId;
        this.stateSwitcher = stateSwitcher;
    }

    @Override
    public Player registerPlayer(String username, GameEventListener eventListener) throws GameException {
        if (usernames.containsKey(username))
            throw new GameException("Username `" + username + "` is given");
        if (players.size() >= maxPlayers) {
            throw new GameException("Too many players");
        }
        var player = PlayerFactory.createPlayer(username, eventListener);
        var playerId = player.getPlayerId();

        usernames.put(username, player);
        players.put(playerId, player);
        readyStates.put(playerId, new Ready(player));

        actionPublisher.publishActionExcludingPlayer(new RegisterAction(gameId, player, false), playerId);
        actionPublisher.publishAction(player, new RegisterAction(gameId, player, true));
        actionPublisher.publishAction(player, new PlayersAction(getPlayers()));
        if (leader == null) {
            leader = player;
            actionPublisher.publishActionToAll(new LeaderAction(leader.getUsername()));
        } else {
            actionPublisher.publishAction(player, new LeaderAction(leader.getUsername()));
        }
        for (var r : readyStates.values()) {
            if (r.set(false)) actionPublisher.publishActionExcludingPlayer(new UnreadyAction(r.getPlayer().getUsername()), playerId);
        }
        for (var p : players.values()) {
            if (p instanceof NpcPlayer npc)
                actionPublisher.publishAction(player, new ReadyAction(npc.getUsername()));
        }
        return player;
    }

    public boolean isLeader(String playerId) {
        return leader != null && leader.getPlayerId().equals(playerId);
    }

    public void registerNpcPlayer() throws GameException {
        if (players.size() >= maxPlayers) {
            throw new GameException("Too many players");
        }
        var username = NPC_NAMES.next();
        if (usernames.containsKey(username))
            throw new GameException("Username `" + username + "` is given");

        var npc = PlayerFactory.createNpcPlayer(username);
        var playerId = npc.getPlayerId();

        usernames.put(username, npc);
        players.put(playerId, npc);
        readyStates.put(playerId, new NpcReady(npc));

        actionPublisher.publishActionToAll(new RegisterAction(gameId, npc, false));
        for (var r : readyStates.values()) {
            if (r.set(false)) actionPublisher.publishActionToAll(new UnreadyAction(r.getPlayer().getUsername()));
        }
        actionPublisher.publishActionToAll(new ReadyAction(npc.getUsername()));
    }

    @Override
    public void removePlayer(String playerId) {
        var player = removePlayerInternal(playerId);
        if (player == null) return;

        var nonNpcs = findNonNpcPlayers();
        if (nonNpcs.isEmpty()) {
            players.clear();
            usernames.clear();
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

    public void kickPlayer(String leaderId, String username) throws GameException {
        if (!isLeader(leaderId)) {
            throw new GameException("Player is not leader of lobby %s.".formatted(gameId));
        }
        var playerKick = usernames.get(username);
        if (playerKick == null) {
            return;
        }
        if (playerKick.getPlayerId().equals(leaderId)) {
            throw new GameException("Cannot kick leader.");
        }

        removePlayerInternal(playerKick.getPlayerId());
        for (var ready : readyStates.values()) {
            if (ready.set(false))
                actionPublisher.publishActionToAll(new UnreadyAction(ready.getPlayer().getUsername()));
        }
        actionPublisher.publishAction(playerKick, new DisqualifiedAction());
        actionPublisher.publishActionExcludingPlayer(new RemovePlayerAction(playerKick, 0), playerKick.getPlayerId());
    }

    private Player removePlayerInternal(String playerId) {
        var player = players.remove(playerId);
        if (player == null)
            return null;

        usernames.remove(player.getUsername());
        readyStates.remove(playerId);
        return player;
    }

    private List<Player> findNonNpcPlayers() {
        return players.values().stream().filter(p -> !(p instanceof NpcPlayer)).toList();
    }

    @Override
    public Player getPlayer(String playerId) throws GameException {
        var player = players.get(playerId);
        if (player == null)
            throw new GameException("Player `" + playerId + "` not found");
        return player;
    }

    @Override
    public List<Player> getPlayers() {
        return List.copyOf(players.valueList());
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
        return maxPlayers - players.size();
    }

    private boolean hasEnoughPlayers() {
        return players.size() >= minPlayers;
    }

}
