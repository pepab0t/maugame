package dev.cerios.maugame.mauengine.player;

import dev.cerios.maugame.mauengine.card.Card;
import dev.cerios.maugame.mauengine.game.GameEventListener;
import dev.cerios.maugame.mauengine.game.GamePlayer;
import dev.cerios.maugame.mauengine.game.action.Action;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Player implements GamePlayer {
    @Getter
    @ToString.Include
    @EqualsAndHashCode.Include
    private final String playerId;
    @Getter
    @ToString.Include
    @EqualsAndHashCode.Include
    private final String username;

    @Getter
    private boolean finished = false;
    @Getter
    private final List<Card> hand = new ArrayList<>();
    @Getter(AccessLevel.PACKAGE)
    private final GameEventListener eventListener;

    Player(String playerId, String username, GameEventListener eventListener) {
        this.playerId = playerId;
        this.eventListener = eventListener;
        this.username = username;
    }

    void deactivate() {
        if (!finished) {
            finished = true;
        }
    }

    void activate() {
        finished = false;
    }

    void trigger(Action action) {
        eventListener.accept(this, action);
    }
}
