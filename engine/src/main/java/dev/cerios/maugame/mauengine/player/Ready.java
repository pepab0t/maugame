package dev.cerios.maugame.mauengine.player;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class Ready {
    protected boolean ready = false;
    @Getter
    protected final Player player;

    public boolean set(boolean ready) {
        var changed = ready != this.ready;
        this.ready = ready;
        return changed;
    }

    public boolean get() {
        return ready;
    }
}

class NpcReady extends Ready {

    NpcReady(Player player) {
        super(player);
        this.ready = true;
    }

    public boolean set(boolean ready) {
        return false;
    }
}
