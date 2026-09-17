package com.example.custompictureframes.storage;

import com.example.custompictureframes.image.ImagePipeline;
import com.example.custompictureframes.model.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PaintingStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long DISK_QUOTA = 2L * 1024 * 1024 * 1024;
    private final Path root;
    private final Map<String, PaintingRecord> records = new ConcurrentHashMap<>();
    public PaintingStore(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root.resolve("originals")); Files.createDirectories(root.resolve("paintings"));
        try (var paths = Files.list(root.resolve("paintings"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                try {
                    if (Files.size(path) > 4096) continue;
                    PaintingRecord r = JSON.fromJson(Files.readString(path), PaintingRecord.class);
                    if (r != null && Limits.validId(r.id()) && Limits.validId(r.sourceId()) && r.spec() != null
                            && path.getFileName().toString().equals(r.id() + ".json")
                            && Files.isRegularFile(texturePath(r.id()))) records.put(r.id(), r);
                } catch (RuntimeException ignored) { /* A corrupt manifest must not prevent loading the world. */ }
            }
        }
    }
    public PaintingRecord get(String id) { return records.get(id); }
    public Collection<PaintingRecord> all() { return List.copyOf(records.values()); }
    public byte[] thumbnail(String id) throws IOException {
        if (!records.containsKey(id)) throw new IOException("Unknown painting");
        Path path = root.resolve("paintings").resolve(id + ".thumb.png");
        if (Files.isRegularFile(path) && Files.size(path) <= Limits.MAX_BYTES) return Files.readAllBytes(path);
        byte[] bytes = ImagePipeline.png(ImagePipeline.resize(ImagePipeline.decode(read(id)), 256));
        atomicWrite(path, bytes);
        return bytes;
    }
    private Path texturePath(String id) {
        if (!Limits.validId(id)) throw new IllegalArgumentException("Invalid asset id");
        return root.resolve("paintings").resolve(id + ".png");
    }
    public byte[] read(String id) throws IOException {
        if (!records.containsKey(id)) throw new IOException("Unknown painting");
        Path path = texturePath(id);
        if (Files.size(path) > Limits.MAX_BYTES) throw new IOException("Stored image too large");
        return Files.readAllBytes(path);
    }
    public synchronized PaintingRecord create(byte[] upload, PaintingSpec spec) throws IOException {
        return create(upload, spec, "");
    }
    public synchronized PaintingRecord create(byte[] upload, PaintingSpec spec, String name) throws IOException {
        var source = ImagePipeline.decode(upload);
        byte[] original = ImagePipeline.png(source);
        if (original.length > Limits.MAX_BYTES) throw new IOException("Normalized PNG exceeds 8 MiB");
        String sourceId = ImagePipeline.hash(original);
        String id = ImagePipeline.paintingId(sourceId, spec);
        if (records.containsKey(id)) {
            PaintingRecord old = records.get(id);
            PaintingRecord named = new PaintingRecord(id, sourceId, spec, old.pixelWidth(), old.pixelHeight(), name);
            if (!old.equals(named)) saveRecord(named);
            return named;
        }
        var rendered = ImagePipeline.render(source, spec, Limits.TEXTURE_SIDE);
        byte[] texture = ImagePipeline.png(rendered);
        if (texture.length > Limits.MAX_BYTES) throw new IOException("Texture exceeds 8 MiB");
        long size;
        try (var paths = Files.walk(root)) {
            size = paths.filter(Files::isRegularFile).mapToLong(p -> {
                try { return Files.size(p); } catch (IOException e) { return DISK_QUOTA; }
            }).sum();
        }
        Path originalPath = root.resolve("originals").resolve(sourceId + ".png");
        long needed = texture.length + (Files.exists(originalPath) ? 0 : original.length) + 4096;
        if (size + needed > DISK_QUOTA) throw new IOException("World painting storage quota reached (2 GiB)");
        if (!Files.exists(originalPath)) atomicWrite(originalPath, original);
        atomicWrite(texturePath(id), texture);
        PaintingRecord record = new PaintingRecord(id, sourceId, spec, rendered.getWidth(), rendered.getHeight(), name);
        saveRecord(record);
        return record;
    }
    private void saveRecord(PaintingRecord record) throws IOException {
        atomicWrite(root.resolve("paintings").resolve(record.id() + ".json"), JSON.toJson(record).getBytes(StandardCharsets.UTF_8));
        records.put(record.id(), record);
    }
    public static void atomicWrite(Path path, byte[] data) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".painting-", ".tmp");
        try {
            Files.write(temporary, data);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
