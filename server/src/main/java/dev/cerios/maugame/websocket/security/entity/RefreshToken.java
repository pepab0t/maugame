package dev.cerios.maugame.websocket.security.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String token;

    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private MauUser user;

    public RefreshToken(String token) {
        this.token = token;
    }
}
