package com.japicraft.bomb;

/**
 * One live module per bomb; definitions can be reused across bombs and layouts.
 */
public interface BombModule extends AutoCloseable {
    void hover(Surface.Point cursor);

    void press(Surface.Point cursor);

    boolean solved();

    @Override
    void close();

    @FunctionalInterface
    interface Definition {
        BombModule mount(ModuleContext context);
    }
}
