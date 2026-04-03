package dev.cerios.maugame.websocket.interceptor;

import dev.cerios.maugame.mauengine.exception.GameException;
import dev.cerios.maugame.mauengine.game.GamePlayer;
import dev.cerios.maugame.websocket.exception.InvalidHandshakeException;
import dev.cerios.maugame.websocket.exception.LobbyAlreadyExistsException;
import dev.cerios.maugame.websocket.exception.NotFoundException;
import dev.cerios.maugame.websocket.exception.ServerException;
import dev.cerios.maugame.websocket.exception.security.AuthException;
import dev.cerios.maugame.websocket.interceptor.result.Result;
import dev.cerios.maugame.websocket.security.JwtUtil;
import dev.cerios.maugame.websocket.service.GameService;
import dev.cerios.maugame.websocket.store.GameStorage;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

import static dev.cerios.maugame.websocket.security.CookieUtil.*;
import static dev.cerios.maugame.websocket.security.JwtUtil.ParsedToken;
import static dev.cerios.maugame.websocket.wshandler.ParameterParser.*;

@Component
@RequiredArgsConstructor
public class GameRegisterInterceptor implements HandshakeInterceptor {

    private final GameService gameService;
    private final GameStorage gameStorage;
    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(
        @NonNull ServerHttpRequest request,
        @NonNull ServerHttpResponse response,
        @NonNull WebSocketHandler wsHandler,
        @NonNull Map<String, Object> attributes
    ) {
        var params = (ConnectionParameters) attributes.get("params");
        var cookies = parseCookies(request.getHeaders());

        try {
            var tokenResult = getToken(cookies);
            var player = connectToGame(params, attributes, cookies, tokenResult);
            attributes.put("gamePlayer", player);
            if (response instanceof ServletServerHttpResponse servletResponse) {
                servletResponse.getServletResponse().addCookie(createGlobalCookie(
                    "playerId",
                    player.getPlayerId()
                ));
            } else {
                throw new RuntimeException("no servlet response");
            }
        } catch (GameException | ServerException e) {
            attributes.put("exception", e);
        }
        return true;
    }

    private GamePlayer connectToGame(
        ConnectionParameters params,
        Map<String, Object> attributes,
        Map<String, String> cookies,
        Result<ParsedToken, AuthException> tokenResult
    ) throws AuthException, LobbyAlreadyExistsException, NotFoundException, GameException, InvalidHandshakeException {
        var username = tokenResult
            .map(ParsedToken::getUsername)
            .or(params::username)
            .getOrThrow();
        return switch (params.decideOperation()) {
            case ConnectRandomData _ -> gameStorage.registerToRandom(username, tokenResult.isSuccessful());
            case ConnectCustomData(String lobby) -> gameStorage.registerToNamed(
                username,
                lobby,
                tokenResult.isSuccessful()
            );
            case CreateData(String lobby, boolean isPrivate) -> gameStorage.registerToNew(username, lobby, isPrivate);
            case ReconnectData _ -> {
                var playerId = cookies.get("playerId");
                if (playerId == null) {
                    throw new InvalidHandshakeException("No cookie `playerId` found");
                }
                attributes.put("reconnect", true);
                yield gameService.reconnectPlayer(username, playerId);
            }
        };
    }

    private Result<ParsedToken, AuthException> getToken(Map<String, String> cookies) throws AuthException {
        var token = cookies.get(TOKEN_COOKIE_NAME);
        if (token == null) {
            return Result.ofError(new AuthException("Missing cookie `token`."));
        }
        try {
            return Result.of(jwtUtil.parse(token));
        } catch (AuthException e) {
            return Result.ofError(e);
        }
    }

    @Override
    public void afterHandshake(
        @NonNull ServerHttpRequest request,
        @NonNull ServerHttpResponse response,
        @NonNull WebSocketHandler wsHandler,
        @Nullable Exception exception
    ) {
    }
}
