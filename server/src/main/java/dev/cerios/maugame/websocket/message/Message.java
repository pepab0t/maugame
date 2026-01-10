package dev.cerios.maugame.websocket.message;

import dev.cerios.maugame.websocket.dto.action.ActionDto;

public sealed interface Message permits ServerMessage, ActionMessage, ErrorMessage {
    MessageType getMessageType();

    static ActionMessage createActionMessage(ActionDto action) {
        return new ActionMessage(action);
    }

    static ErrorMessage createErrorMessage(Exception exception) {
        return new ErrorMessage(exception);
    }
}
