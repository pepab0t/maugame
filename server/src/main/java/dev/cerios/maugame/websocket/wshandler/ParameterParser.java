package dev.cerios.maugame.websocket.wshandler;

import dev.cerios.maugame.websocket.exception.InvalidHandshakeException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@RequiredArgsConstructor
@Component
public class ParameterParser {

    private final Validator validator;

    public ConnectionParameters parse(Map<String, Object> attributes) throws InvalidHandshakeException {
        var username = safelyConvert(attributes.get("user"), this::mapString, null);
        var reconnect = safelyConvert(attributes.get("reconnect"), x -> x.equals("true"), false);
        var lobbyName = safelyConvert(attributes.get("lobby"), this::mapString, null);
        var isNew = safelyConvert(attributes.get("new"), x -> x.equals("true"), false);
        var isPrivate = safelyConvert(attributes.get("private"), x -> x.equals("true"), false);

        var cp = new ConnectionParameters(
            Optional.ofNullable(username),
            reconnect,
            Optional.ofNullable(lobbyName),
            isNew,
            isPrivate
        );

        validateConnectionParams(cp);

        return cp;
    }

    private <T> T safelyConvert(Object obj, Function<Object, T> converter, T defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        return converter.apply(obj);
    }

    private String mapString(Object value) {
        var out = value.toString();
        return out.isBlank() ? null : out;
    }

    private void validateConnectionParams(ConnectionParameters cp) throws InvalidHandshakeException {
        var constraints = validator.validate(cp);
        if (!constraints.isEmpty()) {
            throw new InvalidHandshakeException(constraints.stream().map(ConstraintViolation::getMessage).toList());
        }
    }

    public record ConnectionParameters(
        Optional<@Size(max = 32) String> username,
        boolean reconnect,
        Optional<@Size(max = 50) String> lobbyName,
        Boolean isNew,
        Boolean isPrivate
    ) {

        public ConnectionParameters {
            lobbyName = lobbyName.map(String::strip);
        }

        public OperationData decideOperation() {
            if (reconnect) {
                return new ReconnectData();
            }
            return lobbyName.<OperationData>map(name -> isNew
                    ? new CreateData(name, isPrivate)
                    : new ConnectCustomData(name))
                .orElseGet(ConnectRandomData::new);
        }
    }

    public sealed interface OperationData
        permits ReconnectData, ConnectRandomData, ConnectCustomData, CreateData {
    }

    public record ReconnectData() implements OperationData {}

    public record ConnectRandomData() implements OperationData {}

    public record ConnectCustomData(String lobbyName) implements OperationData {}

    public record CreateData(String lobbyName, boolean isPrivate) implements OperationData {}
}
