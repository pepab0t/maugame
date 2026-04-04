package dev.cerios.maugame.mauengine.game.action;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class DisqualifiedAction implements Action {

    private final String message;

    public DisqualifiedAction() {
        this.message = "";
    }

    @Override
    public ActionType getType() {
        return ActionType.DISQUALIFIED;
    }
}
