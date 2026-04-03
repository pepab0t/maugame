package dev.cerios.maugame.websocket.dto.action;

import dev.cerios.maugame.mauengine.game.action.Action;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DisqualifiedActionDto extends ActionDto {

    private final String message;

    public DisqualifiedActionDto(Action.ActionType type, String message) {
        super(type);
        this.message = message;
    }
}
