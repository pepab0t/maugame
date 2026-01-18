package dev.cerios.maugame.websocket.controller;

import dev.cerios.maugame.websocket.security.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class TestController {

    @GetMapping("/test")
    public Map<String, String> test(@AuthenticationPrincipal AppUserDetails principal) {
        return Map.of("message", "Hello `%s`! Let's play some games.".formatted(principal.getUsername()));
    }
}
