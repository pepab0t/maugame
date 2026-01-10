package dev.cerios.maugame.websocket;

import lombok.experimental.UtilityClass;
import org.springframework.web.socket.TextMessage;

@UtilityClass
public class PayloadUtils {

    public static TextMessage createChatHistoryRequest() {
        return new TextMessage("""
            {
                "requestType": "CHAT",
                "chat": {
                    "chatType": "HISTORY"
                }
            }
            """);
    }

    public static TextMessage createChatMessageRequest(String message) {
        return new TextMessage("""
            {
                "requestType": "CHAT",
                "chat": {
                    "chatType": "MESSAGE",
                    "message": "%s"
                }
            }
            """.formatted(message));
    }
}
