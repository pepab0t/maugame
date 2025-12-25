package dev.cerios.maugame.websocket.dto.action;

import dev.cerios.maugame.mauengine.game.action.Action;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EndActionDto extends ActionDto {

    private final List<String> playerRank;
    private final Map<String, Integer> scores;

    public EndActionDto(Action.ActionType type, List<String> playerRank, Map<String, Integer> scores) {
        super(type);
        this.playerRank = playerRank;
        this.scores = scores;
    }
}
