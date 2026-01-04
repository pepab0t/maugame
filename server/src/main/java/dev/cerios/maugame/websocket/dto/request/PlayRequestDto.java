package dev.cerios.maugame.websocket.dto.request;

import dev.cerios.maugame.mauengine.card.Card;
import dev.cerios.maugame.mauengine.card.Color;
import jakarta.validation.constraints.NotNull;

public record PlayRequestDto(@NotNull Card card, Color nextColor) {
}
