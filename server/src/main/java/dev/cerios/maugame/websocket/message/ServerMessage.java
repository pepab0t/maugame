package dev.cerios.maugame.websocket.message;

import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

import static dev.cerios.maugame.websocket.message.ServerMessage.MessageBody;
import static dev.cerios.maugame.websocket.service.ChatService.ChatMessage;

@ToString
@Getter
public final class ServerMessage<T extends MessageBody> implements Message {
    private final MessageType messageType = MessageType.SERVER_MESSAGE;
    private final T body;

    public ServerMessage(T body) {
        this.body = body;
    }

    public static ServerMessage<InfoMessageBody> ofInfo(String message) {
        return new ServerMessage<>(new InfoMessageBody(message));
    }

    public static ServerMessage<DisconnectMessageBody> ofDisconnect(String username) {return new ServerMessage<>(new DisconnectMessageBody(username));}

    public static ServerMessage<ReconnectMessageBody> ofReconnect(String username) {return new ServerMessage<>(new ReconnectMessageBody(username));}

    public static ServerMessage<ChatMessageBody> ofChatMessage(ChatMessage chatMessage) {
        return new ServerMessage<>(new ChatMessageBody(chatMessage));
    }

    public static ServerMessage<ChatHistoryBody> ofChatHistory(List<ChatMessage> history) {
        return new ServerMessage<>(new ChatHistoryBody(history));
    }

    @Getter
    @ToString
    public abstract static class MessageBody {

        private final BodyType bodyType;

        MessageBody(BodyType bodyType) {
            this.bodyType = bodyType;
        }
    }

    public enum BodyType {
        READY,
        UNREADY,
        DISCONNECT,
        RECONNECT,
        INFO,
        CHAT_MESSAGE,
        CHAT_HISTORY
    }

    @Getter
    @ToString(callSuper = true)
    public static class InfoMessageBody extends MessageBody {
        private final String message;
        private final Instant timestamp = Instant.now();

        public InfoMessageBody(String message) {
            super(BodyType.INFO);
            this.message = message;
        }
    }

    @Getter
    @ToString(callSuper = true)
    public static class DisconnectMessageBody extends MessageBody {
        private final String username;

        public DisconnectMessageBody(String username) {
            super(BodyType.DISCONNECT);
            this.username = username;
        }
    }

    @Getter
    @ToString(callSuper = true)
    public static class ReconnectMessageBody extends MessageBody {
        private final String username;

        public ReconnectMessageBody(String username) {
            super(BodyType.RECONNECT);
            this.username = username;
        }
    }

    @Getter
    @ToString(callSuper = true)
    public static class ChatMessageBody extends MessageBody {
        private final ChatMessage message;

        public ChatMessageBody(ChatMessage message) {
            super(BodyType.CHAT_MESSAGE);
            this.message = message;
        }
    }

    @Getter
    @ToString(callSuper = true)
    public static class ChatHistoryBody extends MessageBody {
        private final List<ChatMessage> history;

        public ChatHistoryBody(List<ChatMessage> history) {
            super(BodyType.CHAT_HISTORY);
            this.history = history;
        }
    }
}