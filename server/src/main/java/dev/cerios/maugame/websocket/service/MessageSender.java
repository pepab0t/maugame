package dev.cerios.maugame.websocket.service;

import dev.cerios.maugame.mauengine.game.GamePlayer;
import dev.cerios.maugame.mauengine.game.action.*;
import dev.cerios.maugame.websocket.dto.action.ActionDto;
import dev.cerios.maugame.websocket.exception.MauTimeoutException;
import dev.cerios.maugame.websocket.mapper.ActionMapper;
import dev.cerios.maugame.websocket.message.Message;
import dev.cerios.maugame.websocket.store.PlayerStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static dev.cerios.maugame.mauengine.locking.LockUtils.wrapLock;

@Service
@Slf4j
public class MessageSender {

    private final ExecutorService executor;
    private final PlayerStore storage;
    private final JsonMapper jsonMapper;
    private final ActionMapper actionMapper;

    private final Lock lock = new ReentrantLock();

    public MessageSender(
        ExecutorService executor,
        PlayerStore storage,
        JsonMapper jsonMapper,
        ActionMapper actionMapper
    ) {
        this.executor = executor;
        this.storage = storage;
        this.jsonMapper = jsonMapper;
        this.actionMapper = actionMapper;
    }

    public void enqueue(GamePlayer player, Action action) {
        enqueueInternal(player.getPlayerId(), () -> enqueueAction(player, action));
    }

    public void enqueue(String playerId, Message message) {
        enqueueInternal(playerId, () -> enqueueMessage(playerId, message));
    }

    private void enqueueInternal(String playerId, Runnable runnable) {
        var ps = storage.getPlayerSources(playerId);
        if (ps == null)
            return;
        var q = ps.queue();
        q.add(runnable);
        executor.execute(wrapLock(ps.lock(), () -> q.remove().run()));
    }

    private void enqueueMessage(String playerId, Message message) {
        try {
            var session = storage.getSession(playerId);

            sendMessage(
                session,
                new TextMessage(jsonMapper.writeValueAsString(message))
            );

            log.debug("send to {} message: {}", playerId, message);
        } catch (JacksonException e) {
            log.info("error during serialization", e);
        } catch (MauTimeoutException ignore) {
        }
    }

    private void enqueueAction(GamePlayer player, Action a) {
        try {
            var session = storage.getSession(player.getPlayerId());
            var dto = mapAction(a);

            sendMessage(
                session,
                new TextMessage(jsonMapper.writeValueAsString(Message.createActionMessage(dto)))
            );

            switch (a.getType()) {
                case DISQUALIFIED, DESTROY -> storage.removePlayerById(player.getPlayerId());
                default -> {
                }
            }
            log.debug("send to {} ({}) action: {}", player.getPlayerId(), player.getUsername(), dto);
        } catch (JacksonException e) {
            log.info("error during serialization", e);
        } catch (MauTimeoutException ignore) {
        }
    }

    private void sendMessage(WebSocketSession session, TextMessage message) {
        try {
            session.sendMessage(message);
        } catch (IOException | IllegalStateException exception) {
            log.debug("error sending message {}", message.getPayload(), exception);
        }
    }

    private ActionDto mapAction(Action action) {
        return switch (action) {
            case ActivateAction a -> actionMapper.toDto(a);
            case DeactivateAction a -> actionMapper.toDto(a);
            case DrawAction a -> actionMapper.toDto(a);
            case EndAction a -> actionMapper.toDto(a);
            case HiddenDrawAction a -> actionMapper.toDto(a);
            case LoseAction a -> actionMapper.toDto(a);
            case PassAction a -> actionMapper.toDto(a);
            case PlayCardAction a -> actionMapper.toDto(a);
            case PlayersAction a -> actionMapper.toDto(a);
            case PlayerShiftAction a -> actionMapper.toDto(a);
            case RegisterAction a -> actionMapper.toDto(a);
            case RemovePlayerAction a -> actionMapper.toDto(a);
            case SendRankAction a -> actionMapper.toDto(a);
            case StartAction a -> actionMapper.toDto(a);
            case StartPileAction a -> actionMapper.toDto(a);
            case WinAction a -> actionMapper.toDto(a);
            case DisqualifiedAction a -> actionMapper.toDto(a);
            case ReadyAction a -> actionMapper.toDto(a);
            case UnreadyAction a -> actionMapper.toDto(a);
            case DestroyAction a -> actionMapper.toDto(a);
            case LeaderAction a -> actionMapper.toDto(a);
            default -> throw new IllegalStateException("Unexpected value: " + action);
        };
    }
}
