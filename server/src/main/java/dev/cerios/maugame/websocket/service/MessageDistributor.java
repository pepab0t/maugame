package dev.cerios.maugame.websocket.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class MessageDistributor {

    private final ExecutorService executor;
    private final PlayerStore storage;
    private final ObjectMapper objectMapper;
    private final ActionMapper actionMapper;

    private final Lock lock = new ReentrantLock();

    public MessageDistributor(ExecutorService executor, PlayerStore storage, ObjectMapper objectMapper, ActionMapper actionMapper) {
        this.executor = executor;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.actionMapper = actionMapper;
    }

    public void distribute(GamePlayer player, Action action) {
        try {
            lock.lock();
            var ps = storage.getPlayerSources(player.getPlayerId());
            if (ps == null)
                return;
            ps.queue().add(() -> distributeAction(player, action));
            executor.execute(() -> {
                try {
                    ps.lock().lock();
                    ps.queue().remove().run();
                } finally {
                    ps.lock().unlock();
                }
            });
        } finally {
            lock.unlock();
        }
    }

    public void enqueueMessage(String playerId, Message message) {
        try {
            lock.lock();
            final var ps = storage.getPlayerSources(playerId);
            if (ps == null)
                return;
            var wsMessage = new TextMessage(objectMapper.writeValueAsString(message));
            var session = storage.getSessionInstant(playerId)
                .orElseThrow(() -> new IllegalStateException("Message %s will not be sent, since session for player %s not found.".formatted(message, playerId)));
            ps.queue().add(() -> {
                try {
                    session.sendMessage(wsMessage);
                } catch (IOException e) {
                    log.debug("Message {} could not be sent.", message, e);
                }
            });
            executor.execute(() -> {
                try {
                    ps.lock().lock();
                    ps.queue().remove().run();
                } finally {
                    ps.lock().unlock();
                }
            });
        } catch (IllegalStateException e) {
            log.debug(e.getMessage());
        } catch (JsonProcessingException e) {
            log.debug("Message {} could not be serialized.", message, e);
        } finally {
            lock.unlock();
        }
    }

    private void distributeAction(GamePlayer player, Action a) {
        try {
            var session = storage.getSession(player.getPlayerId());
            var dto = mapAction(a);

            sendMessage(
                session,
                new TextMessage(objectMapper.writeValueAsString(Message.createActionMessage(dto)))
            );

            switch (a.getType()) {
                case DISQUALIFIED, DESTROY -> storage.removePlayerById(player.getPlayerId());
                default -> {
                }
            }
            log.debug("send to {} action: {}", player.getUsername(), dto);
        } catch (JsonProcessingException e) {
            log.info("error during serialization", e);
        } catch (MauTimeoutException ignore) {
        }
    }

    private void sendMessage(WebSocketSession session, TextMessage message) {
        try {
            lock.lock();
            session.sendMessage(message);
        } catch (IOException | IllegalStateException exception) {
            log.trace("error sending message {}", message.getPayload(), exception);
        } finally {
            lock.unlock();
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
