package dev.cerios.maugame.websocket;

import dev.cerios.maugame.mauengine.exception.GameException;
import dev.cerios.maugame.mauengine.game.GamePlayer;
import dev.cerios.maugame.websocket.message.ServerMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final PlayerStore playerStore;
    private final MessageDistributor distributor;

    public void sendChatMessage(String playerId, String message) {
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

        for (var otherPlayer : game.getAllPlayers()) {
            distributor.enqueueMessage(otherPlayer.getPlayerId(), ServerMessage.ofChat(player.getUsername(), message));
        }
    }
}
