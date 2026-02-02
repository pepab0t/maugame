package dev.cerios.maugame.mauengine.game.effect;

import static dev.cerios.maugame.mauengine.game.effect.GameEffect.*;

public sealed interface GameEffect permits DrawEffect, SkipEffect, NoEffect {

    final class NoEffect implements GameEffect {
        public static final NoEffect INSTANCE = new NoEffect();

        private NoEffect() {}
    }

    record DrawEffect(int count) implements GameEffect {
    }

    record SkipEffect() implements GameEffect {
    }
}
