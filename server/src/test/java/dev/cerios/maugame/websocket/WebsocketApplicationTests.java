package dev.cerios.maugame.websocket;

import dev.cerios.maugame.mauengine.player.PlayerFactory;
import dev.cerios.maugame.websocket.exception.RateLimitException;
import dev.cerios.maugame.websocket.request.RequestProcessor;
import dev.cerios.maugame.websocket.store.PlayerStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test", "dev"})
class WebsocketApplicationTests {

    @Autowired
    RequestProcessor requestProcessor;

    @MockitoBean
    PlayerStore storage;

    @Test
    void contextLoads() {
    }

    @Test
    void testParseRequest() throws RateLimitException {
        String json = """
            {
                "requestType": "MOVE",
                "move": {
                    "moveType": "PLAY"
                }
            }
            """;
        var session = mock(StandardWebSocketSession.class);
        when(storage.getPlayer(any())).thenReturn(PlayerFactory.createPlayer("user1", (_, _) -> {}));
        when(session.getId()).thenReturn("session1");

        requestProcessor.process(session.getId(), json);
    }
}
