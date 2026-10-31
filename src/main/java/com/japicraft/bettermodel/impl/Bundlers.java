package com.japicraft.bettermodel.impl;

import com.japicraft.bettermodel.MinestomAdapter;
import java.util.ArrayList;
import java.util.List;
import kr.toxicity.model.api.nms.ModAnimationBundler;
import kr.toxicity.model.api.nms.PacketBundler;
import kr.toxicity.model.api.platform.PlatformPlayer;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.BundlePacket;
import net.minestom.server.network.packet.server.play.EntityEquipmentPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import org.jetbrains.annotations.NotNull;

public final class Bundlers {
    private static final int MAX_BUNDLE_SIZE = 4096;

    private Bundlers() {
    }

    public static @NotNull Simple of(int initialCapacity) {
        return new Simple(new ArrayList<>(initialCapacity));
    }

    public static @NotNull Simple of(ServerPacket... packets) {
        return new Simple(new ArrayList<>(List.of(packets)));
    }

    public static void add(@NotNull PacketBundler bundler, @NotNull ServerPacket packet) {
        if (bundler instanceof Simple simple) {
            simple.add(packet);
        } else if (bundler instanceof Parallel parallel) {
            parallel.add(packet);
        } else {
            throw new IllegalArgumentException("Unsupported bundler " + bundler);
        }
    }

    private static int weight(@NotNull ServerPacket packet) {
        if (packet instanceof EntityMetaDataPacket data) {
            return data.entries().size();
        } else if (packet instanceof EntityEquipmentPacket equipment) {
            return equipment.equipments().size();
        }
        return 1;
    }

    private static void send(@NotNull Player player, @NotNull List<ServerPacket> packets) {
        if (packets.isEmpty()) {
            return;
        }
        if (packets.size() == 1) {
            player.sendPacket(packets.getFirst());
            return;
        }
        for (int start = 0; start < packets.size(); start += MAX_BUNDLE_SIZE) {
            List<ServerPacket> part = packets.subList(start, Math.min(packets.size(), start + MAX_BUNDLE_SIZE));
            List<ServerPacket> bundle = new ArrayList<>(part.size() + 2);
            bundle.add(new BundlePacket());
            bundle.addAll(part);
            bundle.add(new BundlePacket());
            player.sendPackets(bundle);
        }
    }

    public static final class Simple implements PacketBundler {
        private final List<ServerPacket> packets;

        Simple(List<ServerPacket> packets) {
            this.packets = packets;
        }

        public synchronized void add(@NotNull ServerPacket packet) {
            this.packets.add(packet);
        }

        public void send(@NotNull Player player) {
            List<ServerPacket> copy;
            synchronized (this) {
                copy = List.copyOf(this.packets);
            }
            Bundlers.send(player, copy);
        }

        @Override
        public synchronized boolean isEmpty() {
            return this.packets.isEmpty();
        }

        @Override
        public synchronized int size() {
            return this.packets.size();
        }

        @Override
        public void send(@NotNull PlatformPlayer player, @NotNull Runnable onSuccess) {
            if (this.isEmpty()) {
                return;
            }
            this.send(MinestomAdapter.player(player));
            onSuccess.run();
        }
    }

    public static final class Parallel implements PacketBundler {
        private final int threshold;
        private final List<List<ServerPacket>> bundles = new ArrayList<>();
        private List<ServerPacket> current;
        private int weight;

        Parallel(int threshold) {
            this.threshold = threshold;
            this.current = this.newBundle();
        }

        private List<ServerPacket> newBundle() {
            List<ServerPacket> bundle = new ArrayList<>();
            this.bundles.add(bundle);
            this.weight = 0;
            return bundle;
        }

        public synchronized void add(@NotNull ServerPacket packet) {
            if (this.weight > this.threshold) {
                this.current = this.newBundle();
            }
            this.current.add(packet);
            this.weight += Bundlers.weight(packet);
        }

        @Override
        public synchronized boolean isEmpty() {
            return this.current.isEmpty();
        }

        @Override
        public synchronized int size() {
            return this.bundles.stream().mapToInt(List::size).sum();
        }

        @Override
        public void send(@NotNull PlatformPlayer player, @NotNull Runnable onSuccess) {
            List<List<ServerPacket>> copy;
            synchronized (this) {
                if (this.current.isEmpty()) {
                    return;
                }
                copy = this.bundles.stream().map(List::copyOf).toList();
            }
            Player target = MinestomAdapter.player(player);
            for (List<ServerPacket> bundle : copy) {
                Bundlers.send(target, bundle);
            }
            onSuccess.run();
        }
    }

    static final class NoMod implements ModAnimationBundler {
        static final NoMod INSTANCE = new NoMod();

        NoMod() {
        }

        @Override
        public void send(@NotNull PlatformPlayer player) {
        }
    }
}
