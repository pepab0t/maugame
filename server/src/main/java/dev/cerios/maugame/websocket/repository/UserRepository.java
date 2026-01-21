package dev.cerios.maugame.websocket.repository;

import dev.cerios.maugame.websocket.security.entity.MauUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<MauUser, Long> {
    Optional<MauUser> findByUsername(String username);
}
