package dev.cerios.maugame.websocket.service;

import dev.cerios.maugame.mauengine.card.Card;
import dev.cerios.maugame.mauengine.card.Color;
import dev.cerios.maugame.mauengine.exception.GameException;
import dev.cerios.maugame.mauengine.exception.MauEngineBaseException;
import dev.cerios.maugame.mauengine.game.GamePlayer;
import dev.cerios.maugame.mauengine.player.NpcPlayer;
import dev.cerios.maugame.websocket.event.DisconnectEvent;
import dev.cerios.maugame.websocket.exception.NotFoundException;
import dev.cerios.maugame.websocket.message.ServerMessage;
import dev.cerios.maugame.websocket.store.GameStorage;
import dev.cerios.maugame.websocket.store.PlayerStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {
    private final PlayerStore storage;
    private final MessageSender distributor;
    private final GameStorage gameStorage;

    public GamePlayer reconnectPlayer(String username, String playerId) throws NotFoundException {
        try {
            var game = storage.getGame(playerId).orElseThrow(() -> new NotFoundException("Game not found for given player."));
            var player = game.getPlayer(playerId);
            if (!player.getUsername().equals(username)) {
                throw new NotFoundException("Player(id='%s', username='%s') not found".formatted(playerId, username));
            }
            for (var p : game.getAllPlayers()) {
                var otherId = p.getPlayerId();
                if (otherId.equals(playerId)) {
                    continue;
                }
                distributor.enqueue(otherId, ServerMessage.ofReconnect(username));
            }
            return player;
        } catch (GameException e) {
            storage.removePlayerById(playerId);
            throw new NotFoundException(e.getMessage());
        }
    }

    @EventListener
    public void disconnectPlayer(DisconnectEvent event) {
        var pair = storage.removePlayerBySession(event.sessionId());
        var player = pair.player();
        var gameOpt = pair.game();
        if (gameOpt.isPresent()) {
            var game = gameOpt.get();
            if (game.getPlayerCount() == 0) {
                gameStorage.remove(game.getId());
            } else {
                var playerId = player.getPlayerId();
                var disconnectMessage = ServerMessage.ofDisconnect(player.getUsername());
                for (var otherPlayer : game.getAllPlayers()) {
                    if (otherPlayer instanceof NpcPlayer || otherPlayer.getPlayerId().equals(playerId)) {
                        continue;
                    }
                    distributor.enqueue(otherPlayer.getPlayerId(), disconnectMessage);
                }
            }
        }
    }

    public void registerNpc(String playerId) throws NotFoundException, GameException {
        var game = storage.getGame(playerId)
            .orElseThrow(() -> new NotFoundException("Game not found for given player."));
        game.addNpc(playerId);
    }

    public void kickPlayer(String playerId, String npcName) throws NotFoundException, GameException {
        var game = storage.getGame(playerId)
            .orElseThrow(() -> new NotFoundException("Game not found for given player."));
        game.kickPlayer(playerId, npcName);
    }

    public void setPlayerReady(String playerId) throws NotFoundException, GameException {
        var gameOpt = storage.getGame(playerId);
        if (gameOpt.isEmpty()) {
            throw new NotFoundException("Game not found for given player.");
        }
        gameOpt.get().setReady(playerId);
    }

    public void setPlayerUnready(String playerId) throws NotFoundException, GameException {
        var gameOpt = storage.getGame(playerId);
        if (gameOpt.isEmpty()) {
            throw new NotFoundException("Game not found for given player.");
        }
        gameOpt.get().setUnready(playerId);
    }

    public void playCard(String playerId, Card card, Color nextColor) throws MauEngineBaseException {
        var game = storage.getGame(playerId).orElseThrow(() -> new RuntimeException("No game"));
        game.playCardMove(playerId, card, nextColor);
    }

    public void drawCard(String playerId) throws MauEngineBaseException {
        var game = storage.getGame(playerId).orElseThrow(() -> new RuntimeException("No game"));
        game.playDrawMove(playerId);
    }

    public void pass(String playerId) throws MauEngineBaseException {
        var game = storage.getGame(playerId).orElseThrow(() -> new RuntimeException("No game"));
        game.playPassMove(playerId);
    }

    public void endInstantly(String playerId) throws GameException, NotFoundException {
        var game = storage.getGame(playerId)
            .orElseThrow(() -> new NotFoundException("No game for this player."));
        game.endInstantly();
    }

    private void logPlayerAssignment(GamePlayer player, WebSocketSession session) {
        log.info("player `{}` assigned to session `{}`", player, session.getId());
    }
}
