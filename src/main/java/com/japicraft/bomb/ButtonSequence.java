package com.japicraft.bomb;

import java.util.HashSet;
import java.util.List;

/**
 * Puzzle state is independent of rendering and input, so rules can be tested on their own.
 */
public final class ButtonSequence {
    private final List<Integer> order;
    private int progress;

    public ButtonSequence(List<Integer> order) {
        this.order = List.copyOf(order);
        if (order.isEmpty() || new HashSet<>(order).size() != order.size()
            || order.stream().anyMatch(index -> index < 0 || index >= order.size()))
            throw new IllegalArgumentException("Order must be a permutation of button indices");
    }

    public Result press(int index) {
        if (solved() || index < 0 || index >= order.size() || accepted(index)) return Result.IGNORED;
        if (order.get(progress) != index) {
            progress = 0;
            return Result.STRIKE;
        }
        progress++;
        return Result.CORRECT;
    }

    public boolean accepted(int index) {
        return order.subList(0, progress).contains(index);
    }

    public boolean solved() {
        return progress == order.size();
    }

    public enum Result {CORRECT, STRIKE, IGNORED}
}
