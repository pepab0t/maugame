package dev.cerios.maugame.mauengine.card;

import lombok.Setter;

public class CardComparer {
    @Setter
    private volatile Color nextColor;

    public boolean compare(Card pileCard, Card newCard) {
        if (newCard.type() == CardType.QUEEN)
            return true;
        if (nextColor != null)
            return newCard.color() == nextColor;
        return pileCard.color() == newCard.color() || pileCard.type() == newCard.type();
    }

    public void clear() {
        nextColor = null;
    }
}
