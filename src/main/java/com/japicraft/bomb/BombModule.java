package com.japicraft.bomb;

/**
 * one live module per bomb with reusable definitions across layouts
 */
public interface BombModule extends AutoCloseable {
    void hover(Surface.Point cursor);

    void press(Surface.Point cursor);

    boolean solved();

    /**
     * puzzle and visual reset with mounted entities retained
     */
    void reset(Definition definition);

    @Override
    void close();

    @FunctionalInterface
    interface Definition {
        BombModule mount(ModuleContext context);
    }
}
