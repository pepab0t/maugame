package dev.cerios.maugame.websocket.security;

import dev.cerios.maugame.websocket.dto.rest.UserLogin;
import dev.cerios.maugame.websocket.dto.rest.UserRegister;
import dev.cerios.maugame.websocket.exception.security.AuthException;
import dev.cerios.maugame.websocket.exception.security.LoginException;
import dev.cerios.maugame.websocket.exception.security.RegisterException;
import dev.cerios.maugame.websocket.repository.UserRepository;
import dev.cerios.maugame.websocket.security.entity.MauUser;
import dev.cerios.maugame.websocket.security.entity.RefreshToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static dev.cerios.maugame.websocket.security.CookieUtil.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwt;

    @Transactional
    public MauUser login(UserLogin login, HttpServletResponse response) throws LoginException {
        var user = userRepository.findByUsername(login.username())
            .filter(u -> passwordEncoder.matches(login.password(), u.getPassword()))
            .orElseThrow(() -> new LoginException("Invalid username or password"));

        var token = jwt.generateToken(user.getUsername());
        var refreshToken = jwt.generateRefreshToken(user.getUsername());

        var refreshEntity = user.getRefreshToken();
        if (refreshEntity == null) {
            refreshEntity = new RefreshToken(user);
            user.setRefreshToken(refreshEntity);
        }
        refreshEntity.setToken(refreshToken);

        response.addCookie(createTokenCookie(token));
        response.addCookie(createRefreshTokenCookie(refreshToken));

        return user;
    }

    public MauUser register(UserRegister register) throws RegisterException {
        if (!register.password().equals(register.password2())) {
            throw new RegisterException("Passwords do not match");
        }
        var user = new MauUser(register.username(), register.email(), passwordEncoder.encode(register.password()));
        user = userRepository.save(user);
        return user;
    }

    @Transactional
    public String refresh(
        HttpServletRequest request,
        HttpServletResponse response
    ) throws AuthException {
        var rawToken = findCookieValue(request.getCookies(), REFRESH_TOKEN_COOKIE_NAME);
        var claims = jwt.parse(rawToken);
        var username = claims.getUsername();

        final var refreshToken = userRepository.findTokenByUsername(claims.getUsername())
            .orElseThrow(() -> new AuthException("Invalid token username"));

        Optional.ofNullable(refreshToken)
            .filter(t -> rawToken.equals(t.getToken()))
            .orElseThrow(() -> new AuthException("Invalid refresh token"));

        var newRefreshToken = jwt.generateRefreshToken(username);
        refreshToken.setToken(newRefreshToken);
        response.addCookie(createTokenCookie(jwt.generateToken(username)));
        response.addCookie(createRefreshTokenCookie(newRefreshToken));

        return username;
    }
}
