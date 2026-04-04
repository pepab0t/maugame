package dev.cerios.maugame.mauengine.player.store;

import dev.cerios.maugame.mauengine.player.Player;
import lombok.Getter;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class PlayerStore {

    private final LinkedHashMap<String, Player> playersById = new LinkedHashMap<>();
    private final Map<String, Player> playersByUsername = new HashMap<>();
    private final Map<String, Integer> scores = new HashMap<>();
    @Getter
    private final Updates updates = new Updates();

    public Optional<Player> getById(String playerId) {
        return Optional.ofNullable(playersById.get(playerId));
    }

    public Optional<Player> getByUsername(String username) {
        return Optional.ofNullable(playersByUsername.get(username));
    }

    public SequencedCollection<Player> getAll() {
        return Collections.unmodifiableSequencedCollection(playersById.sequencedValues());
    }

    public List<Player> getFiltered(Predicate<Player> filter) {
        return playersById.sequencedValues().stream().filter(filter).toList();
    }

    public int count() {
        return playersById.size();
    }

    public boolean containsUsername(String username) {
        return playersByUsername.containsKey(username);
    }

    public void addPlayer(Player player) {
        playersById.put(player.getPlayerId(), player);
        playersByUsername.put(player.getUsername(), player);
        scores.put(player.getUsername(), 0);
        updates.onInsert(player);
    }

    public void addScore(String username, int score) {
        scores.compute(username, (_, s) -> s == null ? score : s + score);
    }

    public Optional<Player> deleteById(String playerId) {
        var player = playersById.remove(playerId);
        if (player != null) {
            playersByUsername.remove(player.getUsername());
            updates.onRemove(player);
        }
        return Optional.ofNullable(player);
    }

    public Optional<Player> deleteByUsername(String username) {
        var player = playersByUsername.remove(username);
        if (player != null) {
            playersById.remove(player.getPlayerId());
            updates.onRemove(player);
        }
        return Optional.ofNullable(player);
    }

    public <E extends Throwable> Optional<Player> deleteByUsername(
        String username,
        ThrowingConsumer<E> playerConsumer
    ) throws E {
        var player = playersByUsername.remove(username);
        if (player != null) {
            try {
                playerConsumer.consume(player);
            } catch (Throwable throwable) {
                playersByUsername.put(username, player);
                throw (E) throwable;
            }
            playersById.remove(player.getPlayerId());
            updates.onRemove(player);
        }
        return Optional.ofNullable(player);
    }

    public void clear() {
        playersById.clear();
        playersByUsername.clear();
    }

    public static class Updates {

        private final List<Consumer<PlayerChange>> listeners = new LinkedList<>();

        public void consume(Consumer<PlayerChange> listener) {
            listeners.add(listener);
        }

        private void onInsert(Player player) {
            listeners.forEach(l -> l.accept(new PlayerChange(ChangeType.INSERT, player)));
        }

        private void onRemove(Player player) {
            listeners.forEach(l -> l.accept(new PlayerChange(ChangeType.DELETE, player)));
        }
    }

    public record PlayerChange(ChangeType changeType, Player player) {}

    public enum ChangeType {
        INSERT, DELETE;
    }

    @FunctionalInterface
    public interface ThrowingConsumer<E extends Throwable> {
        void consume(Player player) throws E;
    }
}
