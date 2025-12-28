package dev.cerios.maugame.mauengine.player;

import dev.cerios.maugame.mauengine.game.action.Action;

public class NpcPlayer extends Player {

    NpcPlayer(String playerId, String username) {
        super(playerId, username, null);
    }

    @Override
    void trigger(Action action) {
    }
}
