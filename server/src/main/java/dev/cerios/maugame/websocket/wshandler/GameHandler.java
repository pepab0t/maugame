package dev.cerios.maugame.websocket.wshandler;

import dev.cerios.maugame.mauengine.exception.GameException;
import dev.cerios.maugame.mauengine.game.GamePlayer;
import dev.cerios.maugame.websocket.event.DisconnectEvent;
import dev.cerios.maugame.websocket.exception.MauTimeoutException;
import dev.cerios.maugame.websocket.exception.RateLimitException;
import dev.cerios.maugame.websocket.message.Message;
import dev.cerios.maugame.websocket.request.RequestProcessor;
import dev.cerios.maugame.websocket.store.PlayerStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameHandler extends TextWebSocketHandler {

    private final PlayerStore store;
    private final RequestProcessor processor;
    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher publisher;

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession s) throws GameException {
        final var session = new ConcurrentWebSocketSessionDecorator(s, 10_000, 4096);
        var attributes = session.getAttributes();
        var player = Optional.ofNullable(attributes.get("gamePlayer"))
            .map(GamePlayer.class::cast)
            .orElseThrow(() -> new RuntimeException("Game player not found in session attributes (unexpected)."));
        var isReconnect = Optional.ofNullable(attributes.get("reconnect"))
            .map(x -> Boolean.parseBoolean(x.toString()))
            .orElse(false);

        if (isReconnect) {
            store.registerReplaceSession(player, session);
            var game = store.getGame(player.getPlayerId()).orElseThrow();
            game.sendCurrentStateTo(player.getPlayerId());
        } else store.registerSession(player, session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        publisher.publishEvent(new DisconnectEvent(session.getId()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            processor.process(session.getId(), message.getPayload());
        } catch (RateLimitException e) {
            try {
                session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(Message.createErrorMessage(e))));
            } catch (IOException ex) {
                log.warn("error send message", ex);
            }
        } catch (MauTimeoutException e) {
            try {
                session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(Message.createErrorMessage(e))));
                session.close();
            } catch (Exception ex) {
                log.warn("error send message", ex);
            }
        }
    }
}
