package dev.cerios.maugame.websocket.security.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class MauUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private String username;
    @Column(unique = true)
    private String email;
    private String password;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private RefreshToken refreshToken;

    public MauUser(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }
}
