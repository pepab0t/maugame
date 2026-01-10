package dev.cerios.maugame.websocket.exception;

public class RateLimitException extends ServerException {
    public RateLimitException() {
        super("Available tokens exhausted. Slow down friend.");
    }
}
