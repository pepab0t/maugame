package dev.cerios.maugame.mauengine.player;

import dev.cerios.maugame.mauengine.game.GameEventListener;

public class PlayerFactory {

    public Player createNpcPlayer(String username) {
        return new NpcPlayer(PlayerIdGenerator.generatePlayerId(), username);
    }

    public Player createPlayer(String username, GameEventListener eventListener) {
        return new Player(PlayerIdGenerator.generatePlayerId(), username, eventListener);
    }
}
