package dev.cerios.maugame.mauengine.game;

import dev.cerios.maugame.mauengine.card.Card;
import dev.cerios.maugame.mauengine.card.CardManager;
import dev.cerios.maugame.mauengine.card.CardType;
import dev.cerios.maugame.mauengine.card.Color;
import dev.cerios.maugame.mauengine.exception.MauEngineBaseException;
import dev.cerios.maugame.mauengine.game.effect.GameEffect;
import dev.cerios.maugame.mauengine.player.Player;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Predicate;

@RequiredArgsConstructor
@Slf4j
public class AutoPlayHandler {
    private final CardManager cardManager;
    private final GameCore core;
    private final Random random;

    public void computeAutoPlay(Player player) {
        var pileCard = cardManager.peekPile();
        var effect = core.getGameEffect();

        findPossibleCardPlay(player, pileCard, effect)
            .orElse(
                effect == null
                    ? toRunnable(() -> core.performDraw(player.getPlayerId()))
                    : toRunnable(() -> core.performPass(player.getPlayerId()))
            )
            .run();
    }

    private Optional<Runnable> findPossibleCardPlay(Player player, Card pileCard, GameEffect effect) {
        var cardComparer = cardManager.getCardComparer();
        var hand = player.getHand();

        Predicate<Card> cardPredicate = effect == null
            ? card -> cardComparer.compare(pileCard, card)
            : card -> card.type() == pileCard.type();
        var cardsToPlay = hand.stream().filter(cardPredicate).toList();

        if (cardsToPlay.isEmpty()) return Optional.empty();

        var cardToPlay = cardsToPlay.get(random.nextInt(cardsToPlay.size()));
        return Optional.of(
            cardToPlay.type() == CardType.QUEEN ?
                toRunnable(() -> core.performPlayCard(player.getPlayerId(), cardToPlay, mostOccuredColor(hand))) :
                toRunnable(() -> core.performPlayCard(player.getPlayerId(), cardToPlay))
        );
    }

    private Runnable toRunnable(MauAction action) {
        return () -> {
            try {
                action.execute();
            } catch (Exception e) {
                log.error("Error while executing auto play", e);
            }
        };
    }

    private Color mostOccuredColor(List<Card> cards) {
        Map<Color, Integer> colors = new HashMap<>(Color.values().length);
        for (var card : cards) {
            if (card.type() == CardType.QUEEN) continue;
            colors.compute(card.color(), (_, v) -> v == null ? 1 : v + 1);
        }
        return colors.entrySet().stream()
            .max(Comparator.comparingInt(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .orElseThrow();
    }

    @FunctionalInterface
    interface MauAction {
        void execute() throws MauEngineBaseException;
    }
}
