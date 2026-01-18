package dev.cerios.maugame.websocket.interceptor;

import dev.cerios.maugame.mauengine.exception.GameException;
import dev.cerios.maugame.websocket.exception.InvalidHandshakeException;
import dev.cerios.maugame.websocket.exception.ServerException;
import dev.cerios.maugame.websocket.security.JwtUtil;
import dev.cerios.maugame.websocket.service.GameService;
import dev.cerios.maugame.websocket.store.GameStorage;
import dev.cerios.maugame.websocket.wshandler.ParameterParser;
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
import java.util.Optional;

import static dev.cerios.maugame.websocket.security.CookieUtil.*;

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
    )
        throws Exception {
        var params = (ParameterParser.ConnectionParameters) attributes.get("params");
        var cookies = parseCookies(request.getHeaders());

        var username = Optional.ofNullable(cookies.get(TOKEN_COOKIE_NAME))
            .map(t -> jwtUtil.parse(t).getUsername())
            .or(() -> Optional.ofNullable(params.username()))
            .orElseThrow(() -> new InvalidHandshakeException("Missing username"));

        try {
            var player = switch (params.decideOperation()) {
                case CONNECT_RANDOM -> gameStorage.registerToRandom(username);
                case CONNECT_CUSTOM -> gameStorage.registerToNamed(username, params.lobbyName().get());
                case CREATE -> gameStorage.registerToNew(username, params.lobbyName().get(), params.isPrivate());
                case RECONNECT -> {
                    var playerId = cookies.get("playerId");
                    if (playerId == null) {
                        throw new InvalidHandshakeException("No cookie `playerId` found");
                    }
                    yield gameService.reconnectPlayer(params.username(), playerId);
                }
            };
            attributes.put("gamePlayer", player);
            if (response instanceof ServletServerHttpResponse servletResponse) {
                servletResponse.getServletResponse().addCookie(createGlobalCookie("playerId", player.getPlayerId()));
            } else {
                throw new RuntimeException("no servlet response");
            }
            return true;
        } catch (GameException | ServerException _) {
            return false;
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
