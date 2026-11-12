package com.japicraft.environment;

/**
 * Round outcome is separate from the timer's three-strike display encoding.
 */
final class GameRound {
    private int strikes;
    private boolean ended;

    synchronized boolean mistake() {
        if (ended) return false;
        strikes++;
        return strikes > 3 && expire();
    }

    synchronized boolean expire() {
        if (ended) return false;
        ended = true;
        return true;
    }

    synchronized int strikes() {
        return strikes;
    }
}
