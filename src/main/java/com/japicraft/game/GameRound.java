package com.japicraft.game;

/**
 * round outcome independent of the three-strike timer encoding
 */
public final class GameRound {
    private int strikes;
    private boolean ended;

    public synchronized boolean mistake() {
        if (ended) return false;
        strikes++;
        return strikes > 3 && expire();
    }

    public synchronized boolean expire() {
        if (ended) return false;
        ended = true;
        return true;
    }

    public synchronized int strikes() {
        return strikes;
    }
}
