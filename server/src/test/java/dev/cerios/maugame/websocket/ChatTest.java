package dev.cerios.maugame.websocket;

import dev.cerios.maugame.websocket.clientutils.TestClient;
import dev.cerios.maugame.websocket.message.ServerMessage;
import dev.cerios.maugame.websocket.store.GameStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

import static dev.cerios.maugame.websocket.message.ServerMessage.ChatHistoryBody;
import static dev.cerios.maugame.websocket.message.ServerMessage.ChatMessageBody;
import static dev.cerios.maugame.websocket.service.ChatService.ChatMessage;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatTest {

    @LocalServerPort
    private int port;
    private final long defaultTimeout = 3000L;

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private MauSettings mauSettings;

    @Autowired
    private GameStorage gameStorage;

    @BeforeEach
    void setUp() {
        gameStorage.clear();
        mauSettings.restoreDefaults();
    }

    @Test
    void shouldSendChatMessageToPlayersInGame() throws IOException {
        mauSettings.setMaxPlayers(2);
        var client1 = createTestClient("user1");
        var client2 = createTestClient("user2");
        var client3 = createTestClient("user3");
        var expectedUsername = "user1";
        var expectedMessage = "Hello, world!";

        try (var s1 = client1.handshakeWithCatch().join();
             var _ = client2.handshakeWithCatch().join();
             var _ = client3.handshakeWithCatch().join()) {
            client1.get(3);
            client2.get(2);
            client3.get(2);

            s1.sendMessage(createChatMessageRequest(expectedMessage));
            var received1 = mapper.readValue(client1.get(), new TypeReference<ServerMessage<ChatMessageBody>>() {});
            var received2 = mapper.readValue(client2.get(), new TypeReference<ServerMessage<ChatMessageBody>>() {});
            assertThat(received1.getBody().getMessage().message()).isEqualTo(expectedMessage);
            assertThat(received1.getBody().getMessage().username()).isEqualTo(expectedUsername);
            assertThat(received2.getBody().getMessage().message()).isEqualTo(expectedMessage);
            assertThat(received2.getBody().getMessage().username()).isEqualTo(expectedUsername);
            assertThat(client3.getReceivedMessages()).noneSatisfy(message -> assertThat(message).contains("CHAT"));
        }
    }

    @Test
    void shouldGetTheChatHistory() throws IOException {
        mauSettings.setMaxPlayers(2);
        var client1 = createTestClient("user1");
        var client2 = createTestClient("user2");
        var client3 = createTestClient("user3");
        var expectedMessages = List.of(
            "Hey Guys!",
            "Let's play the game",
            "We will see who is the best!"
        );

        WebSocketSession s2 = null;
        WebSocketSession s3 = null;
        try (var s1 = client1.handshakeWithCatch().join()) {
            client1.get(2);
            for (String message : expectedMessages) {
                s1.sendMessage(createChatMessageRequest(message));
            }
            client1.get(expectedMessages.size());
            s2 = client2.handshakeWithCatch().join();
            s3 = client3.handshakeWithCatch().join();
            client2.get(2);
            client3.get(2);

            s2.sendMessage(createChatHistoryRequest());
            s3.sendMessage(createChatHistoryRequest());
            var history2 = mapper.readValue(client2.get(), new TypeReference<ServerMessage<ChatHistoryBody>>() {});
            var history3 = mapper.readValue(client3.get(), new TypeReference<ServerMessage<ChatHistoryBody>>() {});
            assertThat(history2.getBody().getHistory().stream().map(ChatMessage::message)).containsExactlyElementsOf(expectedMessages);
            assertThat(history3.getBody().getHistory()).isEmpty();
        } finally {
            if (s2 != null) s2.close();
            if (s3 != null) s3.close();
        }
    }

    @Test
    void shouldKeepLastNElementsInChatHistory() throws IOException {
        mauSettings.setMaxChatSize(2);
        var client1 = createTestClient("user1");
        var messages = List.of(
            "message 1",
            "message 2",
            "message 3"
        );

        try (var s1 = client1.handshakeWithCatch().join()) {
            client1.get(2);

            for (String message : messages) {
                s1.sendMessage(createChatMessageRequest(message));
            }
            client1.get(messages.size());

            s1.sendMessage(createChatHistoryRequest());
            var history = mapper.readValue(client1.get(), new TypeReference<ServerMessage<ChatHistoryBody>>() {});
            assertThat(history.getBody().getHistory().stream().map(ChatMessage::message))
                .containsExactlyElementsOf(messages.subList(1, messages.size()));
        }
    }

    private TestClient createTestClient(String username) {
        return new TestClient(TestClient.createConnectionUri(port, username), defaultTimeout);
    }

    private TextMessage createChatHistoryRequest() {
        return new TextMessage("""
            {
                "requestType": "CHAT",
                "chat": {
                    "chatType": "HISTORY"
                }
            }
            """);
    }

    private TextMessage createChatMessageRequest(String message) {
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
