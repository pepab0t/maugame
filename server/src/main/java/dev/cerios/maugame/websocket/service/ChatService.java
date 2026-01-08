package dev.cerios.maugame.websocket.service;

import dev.cerios.maugame.mauengine.exception.GameException;
import dev.cerios.maugame.mauengine.game.Game;
import dev.cerios.maugame.mauengine.game.GamePlayer;
import dev.cerios.maugame.websocket.MauSettings;
import dev.cerios.maugame.websocket.message.ServerMessage;
import dev.cerios.maugame.websocket.store.PlayerStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static dev.cerios.maugame.websocket.locking.LockUtils.runLocked;

@RequiredArgsConstructor
@Slf4j
@Service
public class ChatService {

    private final Map<UUID, Queue<ChatMessage>> gameChats = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final PlayerStore playerStore;
    private final MessageDistributor distributor;
    private final MauSettings settings;

    public void sendChatMessage(String playerId, String message) {
        runLocked(lock.writeLock(), () -> sendChatMessageInternal(playerId, message));
    }

    public void getLastChatMessages(String playerId) {
        runLocked(lock.readLock(), () -> getLastChatMessagesInternal(playerId));
    }

    private void sendChatMessageInternal(String playerId, String message) {
        message = stripAndValidateMessage(message);
        var gameOpt = playerStore.getGame(playerId);
        if (gameOpt.isEmpty()) {
            log.debug("No game found for player {}", playerId);
            return;
        }
        var game = gameOpt.get();
        GamePlayer player;
        try {
            player = game.getPlayer(playerId);
        } catch (GameException e) {
            log.debug("Player {} not found in game {}", playerId, game);
            return;
        }

        var chatMessage = new ChatMessage(player.getUsername(), message);

        provideGameChat(game.getId())
            .add(chatMessage);

        for (var otherPlayer : game.getAllPlayers()) {
            distributor.enqueue(otherPlayer.getPlayerId(), ServerMessage.ofChatMessage(chatMessage));
        }
    }

    private void getLastChatMessagesInternal(String playerId) {
        var history = playerStore.getGame(playerId)
            .map(Game::getId)
            .map(gameChats::get)
            .map(q -> q.stream().toList())
            .orElse(Collections.emptyList());
        distributor.enqueue(playerId, ServerMessage.ofChatHistory(history));
    }

    private String stripAndValidateMessage(String originalMessage) {
        var message = originalMessage.strip();
        if (message.isBlank() || message.length() > 100) {
            throw new IllegalArgumentException("Message must be between 1 and 100 characters long.");
        }
        return message;
    }

    private Queue<ChatMessage> provideGameChat(UUID gameId) {
        return gameChats.computeIfAbsent(gameId, _ -> new CircularFifoQueue<>(settings.getMaxChatSize()));
    }

    public record ChatMessage(String username, String message, Instant timestamp) {
        public ChatMessage(String username, String message) {
            this(username, message, Instant.now());
        }
    }
}
