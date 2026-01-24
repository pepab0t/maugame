package dev.cerios.maugame.websocket.controller;

import dev.cerios.maugame.websocket.dto.rest.UserLogin;
import dev.cerios.maugame.websocket.dto.rest.UserRegister;
import dev.cerios.maugame.websocket.dto.rest.UserResponseDto;
import dev.cerios.maugame.websocket.exception.security.AuthException;
import dev.cerios.maugame.websocket.exception.security.LoginException;
import dev.cerios.maugame.websocket.exception.security.RegisterException;
import dev.cerios.maugame.websocket.security.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static dev.cerios.maugame.websocket.security.CookieUtil.createRefreshTokenCookie;
import static dev.cerios.maugame.websocket.security.CookieUtil.createTokenCookie;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public UserResponseDto login(
        @RequestBody @Valid UserLogin login,
        HttpServletResponse response
    ) throws LoginException {
        var user = authService.login(login, response);
        log.info("logged in user '{}'", user.getUsername());
        return new UserResponseDto("Logged in!", user.getUsername());
    }

    @PostMapping("/register")
    public UserResponseDto register(@RequestBody @Valid UserRegister register) throws RegisterException {
        var user = authService.register(register);
        log.info("registered user '{}'", user);
        return new UserResponseDto("User registered successfully!", user.getUsername());
    }

    @PostMapping("/refresh")
    public UserResponseDto refresh(
        HttpServletRequest request,
        HttpServletResponse response
    ) throws AuthException {
        var user = authService.refresh(request, response);
        log.info("refreshed user '{}'", user.getUsername());
        return new UserResponseDto("Refreshed successfully!", user.getUsername());
    }

    @PostMapping("/logout")
    public UserResponseDto logout(
        @AuthenticationPrincipal UserDetails user,
        HttpServletResponse response
    ) {
        response.addCookie(createTokenCookie(null));
        response.addCookie(createRefreshTokenCookie(null));
        return new UserResponseDto("Logged out!", user == null ? null : user.getUsername());
    }
}
