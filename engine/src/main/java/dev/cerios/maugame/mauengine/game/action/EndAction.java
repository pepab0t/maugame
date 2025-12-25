package dev.cerios.maugame.mauengine.game.action;

import java.util.List;
import java.util.Map;

public record EndAction(List<String> playerRank, Map<String, Integer> scores) implements Action {
    @Override
    public ActionType getType() {
        return ActionType.END_GAME;
    }
}
