package dev.cerios.maugame.websocket.dto;

public record UserRegister(
    String username,
    String email,
    String password,
    String password2
) {
}
