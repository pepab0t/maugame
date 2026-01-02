package dev.cerios.maugame.mauengine.player;

import dev.cerios.maugame.mauengine.game.GameEventListener;

public class PlayerFactory {

    public static Player createNpcPlayer(String username) {
        return new NpcPlayer(PlayerIdGenerator.generate(), username);
    }

    public static Player createPlayer(String username, GameEventListener eventListener) {
        return new Player(PlayerIdGenerator.generate(), username, eventListener);
    }
}
