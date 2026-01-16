package dev.cerios.maugame.websocket.auth;

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
    public static @NonNull Cookie createCookie(String name, String value) {
        var cookie = new Cookie(name, value);
        cookie.setPath("/game");
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
