package dev.cerios.maugame.websocket.controller;

import dev.cerios.maugame.websocket.exception.security.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ControllerAdvice {

    @ExceptionHandler(AuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleAuthException(AuthException e) {
        return Map.of(
            "error",
            e.getMessage(),
            "timestamp",
            Instant.now(),
            "cause",
            e.getClass().getSimpleName()
        );
    }
}
