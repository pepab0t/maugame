package dev.cerios.maugame.mauengine.game.action;

public record LeaderAction(String leader) implements Action {
    @Override
    public ActionType getType() {
        return ActionType.LEADER;
    }
}
