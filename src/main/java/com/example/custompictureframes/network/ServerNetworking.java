package com.example.custompictureframes.network;

import com.example.custompictureframes.CustomPictureFramesMod;
import com.example.custompictureframes.item.PaintingItem;
import com.example.custompictureframes.model.*;
import com.example.custompictureframes.storage.PaintingStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public final class ServerNetworking {
    private static final Map<MinecraftServer, Session> SESSIONS = new HashMap<>();
    public static PaintingStore store(MinecraftServer server) {
        Session s = SESSIONS.get(server); return s == null ? null : s.store;
    }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(PaintingPayload.ID, PaintingPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PaintingPayload.ID, PaintingPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try { SESSIONS.put(server, new Session(server)); }
            catch (IOException e) { CustomPictureFramesMod.LOGGER.error("Cannot initialize painting storage", e); }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Session s = SESSIONS.remove(server);
            if (s != null) { s.closed = true; s.worker.shutdownNow(); s.uploads.clear(); s.downloads.clear(); }
        });
        ServerPlayNetworking.registerGlobalReceiver(PaintingPayload.ID, (payload, context) -> {
            Session s = SESSIONS.get(context.server());
            if (s == null) { send(context.player(), PaintingPayload.result("", "Painting storage unavailable")); return; }
            s.receive(context.player(), payload);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Session s = SESSIONS.get(server); if (s != null) s.forget(handler.player.getUuid());
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Session s = SESSIONS.get(server);
            if (s != null) s.store.all().stream().limit(256).forEach(r -> send(handler.player, catalog(r)));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Session s = SESSIONS.get(server); if (s != null) s.tick();
        });
    }
    private static PaintingPayload catalog(PaintingRecord r) {
        return new PaintingPayload(PaintingPayload.CATALOG, r.id(), 0, 0, r.spec(), new byte[0], r.name());
    }
    private static void send(ServerPlayerEntity player, PaintingPayload packet) {
        if (ServerPlayNetworking.canSend(player, PaintingPayload.ID)) ServerPlayNetworking.send(player, packet);
    }
    private record Upload(String id, PaintingSpec spec, String name, TransferBuffer buffer) {}
    private static final class Download {
        final String id; final byte[] data; final PaintingSpec spec; final boolean thumbnail; int offset, index;
        Download(String id, byte[] data, PaintingSpec spec, boolean thumbnail) { this.id=id; this.data=data; this.spec=spec; this.thumbnail=thumbnail; }
    }
    private static final class Session {
        final MinecraftServer server;
        final PaintingStore store;
        final ThreadPoolExecutor worker = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8), r -> { Thread t = new Thread(r, "painting-storage"); t.setDaemon(true); return t; });
        final Map<UUID, Upload> uploads = new HashMap<>();
        final Map<UUID, Long> cooldown = new HashMap<>();
        final Set<UUID> processing = new HashSet<>();
        final Map<UUID, ArrayDeque<Download>> downloads = new HashMap<>();
        final Set<UUID> reading = new HashSet<>();
        volatile boolean closed;
        Session(MinecraftServer server) throws IOException {
            this.server = server;
            store = new PaintingStore(server.getSavePath(WorldSavePath.ROOT).resolve("custompictureframes"));
        }
        void forget(UUID id) {
            uploads.remove(id); cooldown.remove(id); downloads.remove(id); reading.remove(id);
            // processing is released by completion; retaining it prevents a reconnect bypass.
        }
        boolean connected(ServerPlayerEntity player) {
            return !closed && server.getPlayerManager().getPlayer(player.getUuid()) == player;
        }
        void receive(ServerPlayerEntity p, PaintingPayload packet) {
            UUID who = p.getUuid();
            try {
                switch (packet.kind()) {
                    case PaintingPayload.BEGIN -> {
                        if (p.isSpectator()) throw new IllegalArgumentException("Spectators cannot create paintings");
                        if (processing.contains(who) || uploads.containsKey(who) || uploads.size() >= 8
                                || processing.size() >= 8) throw new IllegalArgumentException("Another upload is in progress; try again");
                        if (System.currentTimeMillis() < cooldown.getOrDefault(who, 0L))
                            throw new IllegalArgumentException("Wait a few seconds before creating another painting");
                        if (!Limits.validId(packet.id())) throw new IllegalArgumentException("Invalid transfer id");
                        cooldown.put(who, System.currentTimeMillis() + 5000);
                        uploads.put(who, new Upload(packet.id(), packet.spec(), PaintingNames.clean(packet.message()), new TransferBuffer(packet.total())));
                    }
                    case PaintingPayload.CHUNK -> {
                        Upload u = uploads.get(who);
                        if (u == null || !u.id().equals(packet.id())) return;
                        u.buffer().append(packet.index(), packet.data());
                        if (u.buffer().complete()) {
                            uploads.remove(who); processing.add(who);
                            byte[] data = u.buffer().bytes();
                            try {
                                worker.execute(() -> {
                                    try {
                                        PaintingRecord r = store.create(data, u.spec(), u.name());
                                        server.execute(() -> {
                                            processing.remove(who);
                                            if (!connected(p)) return;
                                            var stack = PaintingItem.stack(r.id(), r.spec().width(), r.spec().height(), u.name());
                                            if (!p.getInventory().insertStack(stack)) p.dropItem(stack, false);
                                            p.currentScreenHandler.sendContentUpdates();
                                            send(p, PaintingPayload.result(r.id(), ""));
                                            for (var other : server.getPlayerManager().getPlayerList()) send(other, catalog(r));
                                        });
                                    } catch (Exception e) {
                                        CustomPictureFramesMod.LOGGER.warn("Painting import failed: {}", e.toString());
                                        server.execute(() -> { processing.remove(who); if (connected(p)) send(p, PaintingPayload.result("", safe(e))); });
                                    }
                                });
                            } catch (RejectedExecutionException e) { processing.remove(who); throw new IllegalArgumentException("Server busy; retry shortly"); }
                        }
                    }
                    case PaintingPayload.REQUEST, PaintingPayload.REQUEST_THUMB -> {
                        PaintingRecord record = store.get(packet.id());
                        if (record == null) { send(p, PaintingPayload.result(packet.id(), "Painting not found on this server")); return; }
                        var q = downloads.computeIfAbsent(who, k -> new ArrayDeque<>());
                        boolean thumbnail = packet.kind() == PaintingPayload.REQUEST_THUMB;
                        if (reading.contains(who) || q.size() >= 2 || q.stream().anyMatch(d -> d.id.equals(packet.id()) && d.thumbnail == thumbnail)) return;
                        if (reading.size() >= 8 || downloads.values().stream().mapToInt(ArrayDeque::size).sum() >= 16) return;
                        reading.add(who);
                        try {
                            worker.execute(() -> {
                                try {
                                    byte[] data = thumbnail ? store.thumbnail(packet.id()) : store.read(packet.id());
                                    server.execute(() -> {
                                        reading.remove(who); if (!connected(p)) return;
                                        downloads.computeIfAbsent(who, k -> new ArrayDeque<>()).add(new Download(packet.id(), data, record.spec(), thumbnail));
                                    });
                                } catch (IOException e) {
                                    server.execute(() -> { reading.remove(who); if (connected(p)) send(p, PaintingPayload.result(packet.id(), safe(e))); });
                                }
                            });
                        } catch (RejectedExecutionException e) { reading.remove(who); }
                    }
                    default -> { /* Reject server-to-client packet types without side effects. */ }
                }
            } catch (IllegalArgumentException e) {
                uploads.remove(who); send(p, PaintingPayload.result("", safe(e)));
            }
        }
        void tick() {
            uploads.entrySet().removeIf(e -> {
                if (!e.getValue().buffer().expired()) return false;
                var p = server.getPlayerManager().getPlayer(e.getKey());
                if (p != null) send(p, PaintingPayload.result("", "Upload timed out"));
                return true;
            });
            int budget = 16;
            for (var entry : downloads.entrySet()) {
                var p = server.getPlayerManager().getPlayer(entry.getKey());
                if (p == null) continue;
                var q = entry.getValue();
                for (int i = 0; i < 2 && budget > 0 && !q.isEmpty(); i++, budget--) {
                    Download d = q.peek();
                    if (d.offset == 0) send(p, PaintingPayload.of(d.thumbnail ? PaintingPayload.THUMB_BEGIN : PaintingPayload.IMAGE_BEGIN, d.id, 0, d.data.length, d.spec, new byte[0]));
                    int end = Math.min(d.offset + Limits.CHUNK_BYTES, d.data.length);
                    send(p, PaintingPayload.of(d.thumbnail ? PaintingPayload.THUMB_CHUNK : PaintingPayload.IMAGE_CHUNK, d.id, d.index++, d.data.length, d.spec, Arrays.copyOfRange(d.data, d.offset, end)));
                    d.offset = end; if (end == d.data.length) q.remove();
                }
                if (budget == 0) break;
            }
        }
        static String safe(Exception e) {
            String s = e.getMessage() == null ? "Image processing failed" : e.getMessage();
            return s.substring(0, Math.min(240, s.length()));
        }
    }
}
