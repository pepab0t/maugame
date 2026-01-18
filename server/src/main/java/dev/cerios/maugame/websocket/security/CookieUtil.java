package dev.cerios.maugame.websocket.security;

import jakarta.servlet.http.Cookie;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@UtilityClass
public class CookieUtil {

    public static final String TOKEN_COOKIE_NAME = "token";
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    public static Cookie createTokenCookie(String token) {
        return createGlobalCookie(TOKEN_COOKIE_NAME, token);
    }

    public static Cookie createRefreshTokenCookie(String refreshToken) {
        return createCookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken, "/api/auth/refresh");
    }

    public static @NonNull Cookie createGlobalCookie(String name, String value) {
        return createCookie(name, value, "/");
    }

    public static Cookie createCookie(String name, String value, String path) {
        var cookie = new Cookie(name, value);
        cookie.setPath(path);
        cookie.setHttpOnly(true);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }

    public static @NonNull Map<String, String> parseCookies(HttpHeaders headers) {
        return Optional.ofNullable(headers.get("Cookie"))
            .stream()
            .flatMap(Collection::stream)
            .flatMap(raw -> Arrays.stream(raw.split(";")).map(String::trim))
            .map(raw -> raw.split("="))
            .filter(pair -> pair.length == 2)
            .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1]));
    }
}
