package dev.cerios.maugame.websocket.request;

public enum RequestType {
    MOVE,
    CONTROL,
    CHAT;

    public enum MoveType {
        PLAY,
        DRAW,
        PASS
    }

    public enum ControlType {
        READY,
        UNREADY,
        KICK,
        CHEAT_END,
        REGISTER_NPC
    }
}
