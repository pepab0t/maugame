package dev.cerios.maugame.websocket;

import dev.cerios.maugame.websocket.config.MauSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@Slf4j
public class MauWebsocketApplication {
    public static void main(String[] args) {
        SpringApplication.run(MauWebsocketApplication.class, args);
    }

    // TODO game.getPlayer(String username) should return Optional

    @Bean
    public CommandLineRunner commandLineRunner(MauSettings settings) {
        return _ -> log.info("started with settings: {}", settings);
    }
}
