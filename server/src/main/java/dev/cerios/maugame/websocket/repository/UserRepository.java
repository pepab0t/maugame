package dev.cerios.maugame.websocket.repository;

import dev.cerios.maugame.websocket.security.entity.MauUser;
import dev.cerios.maugame.websocket.security.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<MauUser, Long> {
    Optional<MauUser> findByUsername(String username);

    @Query("SELECT mu.refreshToken from MauUser mu where mu.username = :username")
    Optional<RefreshToken> findTokenByUsername(@Param("username") String username);
}
