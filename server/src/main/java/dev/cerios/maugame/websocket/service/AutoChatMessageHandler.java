package dev.cerios.maugame.websocket.service;

import dev.cerios.maugame.websocket.event.RegisterNpcEvent;
import dev.cerios.maugame.websocket.exception.InvalidCommandException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AutoChatMessageHandler {

    private final ChatService chatService;

    @EventListener
    void onNpcRegistered(RegisterNpcEvent event) {
        try {
            chatService.sendChatMessage(event.playerId(), "%s greets you all!".formatted(event.npcName()));
        } catch (InvalidCommandException e) {
            log.error("Npc player registration chat message failed to send for %s".formatted(event), e);
        }
    }
}
