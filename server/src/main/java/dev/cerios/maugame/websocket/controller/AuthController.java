package dev.cerios.maugame.websocket.controller;

import dev.cerios.maugame.websocket.dto.UserLogin;
import dev.cerios.maugame.websocket.dto.UserRegister;
import dev.cerios.maugame.websocket.repository.UserRepository;
import dev.cerios.maugame.websocket.security.JwtUtil;
import dev.cerios.maugame.websocket.security.entity.MauUser;
import dev.cerios.maugame.websocket.security.entity.RefreshToken;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static dev.cerios.maugame.websocket.security.CookieUtil.createRefreshTokenCookie;
import static dev.cerios.maugame.websocket.security.CookieUtil.createTokenCookie;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JwtUtil jwt;

    @PostMapping("/login")
    @Transactional
    public Map<String, String> login(
        @RequestBody @Valid UserLogin login,
        HttpServletResponse response
    ) {
        var user = userRepository.findByUsername(login.username())
            .filter(u -> passwordEncoder.matches(login.password(), u.getPassword()))
            .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        var token = jwt.generateToken(user.getUsername());
        var refreshToken = jwt.generateRefreshToken(user.getUsername());
        user.setRefreshToken(new RefreshToken(refreshToken));

        response.addCookie(createTokenCookie(token));
        response.addCookie(createRefreshTokenCookie(refreshToken));

        return Map.of("message", "Logged in!", "user", user.getUsername());
    }

    @PostMapping("/register")
    public Map<String, String> register(@RequestBody @Valid UserRegister register) {
        if (!register.password().equals(register.password2())) {
            return Map.of("message", "Passwords do not match!");
        }
        var user = new MauUser(register.username(), register.email(), passwordEncoder.encode(register.password()));
        userRepository.save(user);
        log.info("registered user {}", register.username());
        return Map.of("message", "User registered successfully!");
    }
}
