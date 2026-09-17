package com.example.custompictureframes.client.render;

import com.example.custompictureframes.CustomPictureFramesMod;
import com.example.custompictureframes.client.ClientNetworking;
import com.example.custompictureframes.image.ImagePipeline;
import com.example.custompictureframes.model.Limits;
import com.example.custompictureframes.storage.PaintingStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.*;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class TextureCache {
    public static final TextureCache INSTANCE = new TextureCache(false);
    public static final TextureCache THUMBNAILS = new TextureCache(true);
    private final boolean thumbnail;
    private final long gpuLimit;
    private TextureCache(boolean thumbnail) { this.thumbnail = thumbnail; gpuLimit = (thumbnail ? 16L : 128L) * 1024 * 1024; }
    private static final long DISK_LIMIT = 512L * 1024 * 1024;
    private record Entry(Identifier texture, long bytes, long[] lastUse) {}
    private final Map<String, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private final Map<String, Long> pending = new HashMap<>();
    private final Set<String> decoding = new HashSet<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "painting-cache"); t.setDaemon(true); return t; });
    private Path disk;
    private int epoch, maxSide = Limits.TEXTURE_SIDE;
    private long used;
    public void connect(String serverKey) {
        clear();
        disk = MinecraftClient.getInstance().runDirectory.toPath().resolve("custompictureframes/cache")
                .resolve(ImagePipeline.hash(serverKey.getBytes(java.nio.charset.StandardCharsets.UTF_8))).resolve(thumbnail ? "thumbs" : "full");
        maxSide = Math.max(1, Math.min(Limits.TEXTURE_SIDE, GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE)));
    }
    public Identifier get(String id) {
        if (!Limits.validId(id)) return null;
        Entry e = entries.get(id);
        if (e != null) { e.lastUse()[0] = System.currentTimeMillis(); return e.texture(); }
        if (disk == null || decoding.contains(id) || (!pending.containsKey(id) && pending.size() + decoding.size() >= 8)) return null;
        long now = System.currentTimeMillis();
        if (now - pending.getOrDefault(id, 0L) < 10_000) return null;
        pending.put(id, now);
        int generation = epoch; Path path = disk.resolve(id + ".png");
        io.execute(() -> {
            byte[] data = null;
            try { if (Files.isRegularFile(path) && Files.size(path) <= Limits.MAX_BYTES) data = Files.readAllBytes(path); }
            catch (Exception ignored) {}
            byte[] result = data;
            MinecraftClient.getInstance().execute(() -> {
                if (generation != epoch) return;
                if (result != null) accept(id, result, false);
                else ClientNetworking.request(id, thumbnail);
            });
        });
        return null;
    }
    public void failed(String id) { pending.put(id, System.currentTimeMillis() + 20_000); }
    public void accept(String id, byte[] data, boolean save) {
        if (disk == null || !Limits.validId(id) || decoding.contains(id) || decoding.size() >= 8) return;
        decoding.add(id);
        int generation = epoch, limit = maxSide; Path directory = disk;
        io.execute(() -> {
            NativeImage image = null;
            try {
                var buffered = ImagePipeline.decode(data);
                buffered = ImagePipeline.resize(buffered, limit);
                image = com.example.custompictureframes.client.image.NativeImages.fromBuffered(buffered);
                if (save) {
                    PaintingStore.atomicWrite(directory.resolve(id + ".png"), data);
                    pruneDisk(directory.getParent().getParent());
                }
                NativeImage ready = image;
                MinecraftClient.getInstance().execute(() -> {
                    if (generation != epoch) { ready.close(); return; }
                    decoding.remove(id); pending.remove(id);
                    var manager = MinecraftClient.getInstance().getTextureManager();
                    var texture = new NativeImageBackedTexture(ready);
                    Identifier identifier = CustomPictureFramesMod.id("dynamic/" + (thumbnail ? "thumb/" : "full/") + id);
                    manager.registerTexture(identifier, texture); texture.setFilter(true, false);
                    Entry old = entries.put(id, new Entry(identifier, (long)ready.getWidth() * ready.getHeight() * 4, new long[]{System.currentTimeMillis()}));
                    if (old != null) used -= old.bytes();
                    used += (long)ready.getWidth() * ready.getHeight() * 4;
                    evict();
                });
            } catch (Exception | OutOfMemoryError e) {
                if (image != null) image.close();
                try { Files.deleteIfExists(directory.resolve(id + ".png")); } catch (Exception ignored) {}
                MinecraftClient.getInstance().execute(() -> {
                    if (generation == epoch) { decoding.remove(id); pending.remove(id); }
                });
                CustomPictureFramesMod.LOGGER.warn("Cannot load painting texture {}: {}", id, e.toString());
            }
        });
    }
    private static void pruneDisk(Path root) throws java.io.IOException {
        try (var files = Files.walk(root)) {
            var list = files.filter(p -> p.toString().endsWith(".png")).sorted(Comparator.comparingLong(p -> {
                try { return Files.getLastModifiedTime(p).toMillis(); } catch (Exception e) { return 0L; }
            })).toList();
            long total = 0;
            for (Path p : list) total += Files.size(p);
            for (Path p : list) { if (total <= DISK_LIMIT) break; long size = Files.size(p); Files.deleteIfExists(p); total -= size; }
        }
    }
    public void tick() {
        pending.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue() > Limits.TRANSFER_TIMEOUT_MS);
        evict();
    }
    private void evict() {
        long now = System.currentTimeMillis();
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (used <= gpuLimit && now - entry.getValue().lastUse()[0] < 120_000) continue;
            MinecraftClient.getInstance().getTextureManager().destroyTexture(entry.getValue().texture());
            used -= entry.getValue().bytes(); iterator.remove();
        }
    }
    public void clear() {
        epoch++;
        entries.values().forEach(e -> MinecraftClient.getInstance().getTextureManager().destroyTexture(e.texture()));
        entries.clear(); pending.clear(); decoding.clear(); used = 0; disk = null;
    }
}
