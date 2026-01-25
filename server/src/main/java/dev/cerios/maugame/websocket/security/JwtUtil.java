package dev.cerios.maugame.websocket.security;

import dev.cerios.maugame.websocket.config.MauSettings;
import dev.cerios.maugame.websocket.exception.security.AuthException;
import dev.cerios.maugame.websocket.exception.security.AuthExpiredException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class JwtUtil {

    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor("your-very-strong-secret-key-change-me-please"
        .getBytes(StandardCharsets.UTF_8));
    private final JwtParser jwtParser = Jwts.parser().verifyWith(SECRET_KEY).build();

    private final MauSettings settings;

    public JwtUtil(MauSettings settings) {
        this.settings = settings;
    }

    public ParsedToken parse(String token) throws AuthException {
        try {
            return new ParsedToken(jwtParser.parseSignedClaims(token)
                .getPayload());
        } catch (ExpiredJwtException e) {
            throw new AuthExpiredException(e.getMessage());
        } catch (JwtException e) {
            throw new AuthException(e.getMessage(), e);
        }
    }

    public String generateToken(String username) {
        var now = Instant.now();
        return Jwts.builder()
            .subject(username)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(settings.getTokenDurationSeconds()))) // 15 minutes
            .signWith(SECRET_KEY)
            .compact();
    }

    public String generateRefreshToken(String username) {
        var now = Instant.now();
        return Jwts.builder()
            .subject(username)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(settings.getRefreshTokenDurationDays(), ChronoUnit.DAYS)))
            .signWith(SECRET_KEY)
            .compact();
    }

    @RequiredArgsConstructor
    public static class ParsedToken {
        private final Claims claims;

        public String getUsername() {
            return claims.getSubject();
        }

        public Date getExpiration() {
            return claims.getExpiration();
        }

        public boolean isNotExpired() {
            return claims.getExpiration().after(new Date());
        }
    }
}
