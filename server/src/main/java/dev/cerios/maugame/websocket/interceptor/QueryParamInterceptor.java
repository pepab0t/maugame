package dev.cerios.maugame.websocket.interceptor;

import dev.cerios.maugame.websocket.exception.InvalidHandshakeException;
import dev.cerios.maugame.websocket.wshandler.ParameterParser;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class QueryParamInterceptor implements HandshakeInterceptor {

    private final ParameterParser parser;

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        @NonNull ServerHttpResponse response,
        @NonNull WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) throws InvalidHandshakeException {
        Map<String, Object> connectionMap = Optional.ofNullable(request.getURI().getQuery()).stream()
            .flatMap(queryParam -> Arrays.stream(queryParam.split("&")))
            .map(param -> param.split("="))
            .collect(Collectors.toMap(param -> param[0], param -> param[1]));

        attributes.put("params", parser.parse(connectionMap));
        return true;
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception ex
    ) {
        // No-op
    }
}
