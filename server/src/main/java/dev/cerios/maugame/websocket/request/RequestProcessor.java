package dev.cerios.maugame.websocket.request;

import dev.cerios.maugame.mauengine.exception.MauEngineBaseException;
import dev.cerios.maugame.websocket.MauSettings;
import dev.cerios.maugame.websocket.dto.request.PlayRequestDto;
import dev.cerios.maugame.websocket.exception.InvalidCommandException;
import dev.cerios.maugame.websocket.exception.NotFoundException;
import dev.cerios.maugame.websocket.exception.RateLimitException;
import dev.cerios.maugame.websocket.mapper.ExceptionMapper;
import dev.cerios.maugame.websocket.ratelimit.RateLimiter;
import dev.cerios.maugame.websocket.service.ChatService;
import dev.cerios.maugame.websocket.service.GameService;
import dev.cerios.maugame.websocket.service.MessageDistributor;
import dev.cerios.maugame.websocket.store.PlayerStore;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class RequestProcessor {

    private final JsonMapper jsonMapper;
    private final ExceptionMapper exceptionMapper;
    private final PlayerStore storage;
    private final GameService gameService;
    private final ChatService chatService;
    private final Validator validator;
    private final MessageDistributor distributor;
    private final MauSettings settings;
    private final RateLimiter rateLimiter;

    public void process(String sessionId, String request) throws RateLimitException {
        if (!rateLimiter.canProceed(sessionId)) {
            throw new RateLimitException();
        }
        log.trace("processing request for session {}", sessionId);
        var player = storage.getPlayer(sessionId);
        var playerId = player.getPlayerId();

        try {
            JsonNode root = jsonMapper.readTree(request);
            var requestType = jsonMapper.convertValue(root.get("requestType"), RequestType.class);

            switch (requestType) {
                case MOVE -> processMove(
                    Optional.ofNullable(root.get("move")).orElseThrow(() -> new InvalidCommandException("Missing field: move")),
                    playerId
                );
                case CONTROL -> processControl(
                    Optional.ofNullable(root.get("control")).orElseThrow(() -> new InvalidCommandException("Missing field: control")),
                    playerId
                );
                case CHAT -> processChat(
                    Optional.ofNullable(root.get("chat")).orElseThrow(() -> new InvalidCommandException("Missing field: message")),
                    playerId
                );
            }
        } catch (Exception e) {
            distributor.enqueue(playerId, exceptionMapper.toErrorResponse(e));
        }
    }

    private void processChat(JsonNode node, String senderId) throws InvalidCommandException {
        var type = jsonMapper.convertValue(
            Optional.ofNullable(node.get("chatType")).orElseThrow(() -> new InvalidCommandException("Missing field: chatType")),
            RequestType.ChatType.class
        );

        switch (type) {
            case MESSAGE -> {
                var message = Optional.ofNullable(node.get("message"))
                    .flatMap(JsonNode::asStringOpt)
                    .orElseThrow(() -> new InvalidCommandException("Missing field: message (string)"));
                chatService.sendChatMessage(senderId, message);
            }
            case HISTORY -> chatService.getLastChatMessages(senderId);
            default -> throw new InvalidCommandException("Invalid chat type");
        }
    }

    private void processMove(JsonNode node, final String playerId) throws InvalidCommandException, MauEngineBaseException {
        var moveType = jsonMapper.convertValue(node.get("moveType"), RequestType.MoveType.class);
        switch (moveType) {
            case PLAY -> {
                var dto = jsonMapper.convertValue(node, PlayRequestDto.class);
                var constraints = validator.validate(dto);
                if (!constraints.isEmpty()) {
                    throw new InvalidCommandException("invalid: " + dto.toString());
                }
                gameService.playCard(playerId, dto.card(), dto.nextColor());
            }
            case DRAW -> gameService.drawCard(playerId);
            case PASS -> gameService.pass(playerId);
        }
    }

    private void processControl(JsonNode node, String playerId) throws InvalidCommandException, MauEngineBaseException, NotFoundException {
        var controlType = jsonMapper.convertValue(node.get("controlType"), RequestType.ControlType.class);

        switch (controlType) {
            case READY -> gameService.setPlayerReady(playerId);
            case UNREADY -> gameService.setPlayerUnready(playerId);
            case REGISTER_NPC -> gameService.registerNpc(playerId);
            case KICK -> gameService.kickPlayer(
                playerId,
                Optional.ofNullable(node.get("username"))
                    .flatMap(JsonNode::asStringOpt)
                    .filter(s -> !s.isBlank())
                    .orElseThrow(() -> new InvalidCommandException("Missing field: username (string)"))
            );
            case CHEAT_END -> {
                if (settings.isCheatingEnabled()) {
                    gameService.endInstantly(playerId);
                } else {
                    throw new InvalidCommandException("Cheating is disabled");
                }
            }
            default -> throw new InvalidCommandException("Invalid control type");
        }
    }
}
