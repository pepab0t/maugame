package dev.cerios.maugame.websocket.controller;

import dev.cerios.maugame.websocket.exception.security.AuthException;
import dev.cerios.maugame.websocket.security.AppUserDetails;
import dev.cerios.maugame.websocket.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static dev.cerios.maugame.websocket.security.CookieUtil.TOKEN_COOKIE_NAME;
import static dev.cerios.maugame.websocket.security.CookieUtil.findCookieValue;

@RestController
@RequestMapping("/api")
public class TestController {

    private final JwtUtil jwt;

    public TestController(JwtUtil jwt) {this.jwt = jwt;}

    @GetMapping("/whoami")
    public Map<String, String> whoAmI(@AuthenticationPrincipal AppUserDetails principal) {
        return Map.of(
            "username", principal.getUsername(),
            "message", "Hello `%s`! Let's play some games.".formatted(principal.getUsername())
        );
    }

    @GetMapping("/time-left")
    public Map<String, Object> getExpiration(HttpServletRequest request) throws AuthException {
        var token = findCookieValue(request.getCookies(), TOKEN_COOKIE_NAME);
        var parsedToken = jwt.parse(token);
        var expiration = parsedToken.getExpiration().toInstant();
        var timeLeft = (double) Duration.between(Instant.now(), expiration).toMillis();
        return Map.of("username", parsedToken.getUsername(), "timeLeftSeconds", timeLeft / 1000);
    }
}
