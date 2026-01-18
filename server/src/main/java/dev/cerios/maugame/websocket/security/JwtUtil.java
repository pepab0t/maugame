package dev.cerios.maugame.websocket.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor("your-very-strong-secret-key-change-me-please"
        .getBytes(StandardCharsets.UTF_8));
    private final JwtParser jwtParser = Jwts.parser().verifyWith(SECRET_KEY).build();

    public ParsedToken parse(String token) {
        return new ParsedToken(jwtParser.parseSignedClaims(token)
            .getPayload());
    }

    public String generateToken(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(username)
            .issuedAt(new Date(now))
            .expiration(new Date(now + 1000 * 60 * 15)) // 15 minutes
            .signWith(SECRET_KEY)
            .compact();
    }

    public String generateRefreshToken(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(username)
            .issuedAt(new Date(now))
            .expiration(new Date(now + 1000L * 60 * 60 * 24 * 30)) // 30 days
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
