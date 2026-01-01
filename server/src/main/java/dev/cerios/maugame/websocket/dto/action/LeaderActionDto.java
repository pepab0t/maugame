package dev.cerios.maugame.websocket.dto.action;

import dev.cerios.maugame.mauengine.game.action.Action;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeaderActionDto extends ActionDto {

    private final String leader;

    public LeaderActionDto(Action.ActionType type, String leader) {
        super(type);
        this.leader = leader;
    }
}
