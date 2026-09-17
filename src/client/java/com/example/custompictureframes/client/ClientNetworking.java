package com.example.custompictureframes.client;

import com.example.custompictureframes.client.render.TextureCache;
import com.example.custompictureframes.item.PaintingItem;
import com.example.custompictureframes.model.*;
import com.example.custompictureframes.network.*;
import com.example.custompictureframes.image.ImagePipeline;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import java.util.*;
import java.util.function.Consumer;

public final class ClientNetworking {
    private static final Map<String, TransferBuffer> incoming = new HashMap<>();
    private static final Map<String, TransferBuffer> thumbnails = new HashMap<>();
    public static final Map<String, ItemStack> LIBRARY = new LinkedHashMap<>();
    private static byte[] upload;
    private static String uploadId;
    private static PaintingSpec uploadSpec;
    private static int offset, index;
    private static long started;
    private static Consumer<String> completion;
    public static String status = "";
    private static boolean libraryChanged;
    public static boolean busy() { return uploadId != null; }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PaintingPayload.ID, (packet, context) -> receive(packet));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            reset();
            String key = client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().address
                    : "local:" + (client.getServer() == null ? "world" : client.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath());
            TextureCache.INSTANCE.connect(key);
            TextureCache.THUMBNAILS.connect(key);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { reset(); TextureCache.INSTANCE.clear(); TextureCache.THUMBNAILS.clear(); });
    }
    public static void request(String id, boolean thumbnail) {
        if (ClientPlayNetworking.canSend(PaintingPayload.ID))
            ClientPlayNetworking.send(PaintingPayload.of(thumbnail ? PaintingPayload.REQUEST_THUMB : PaintingPayload.REQUEST, id, 0, 0, PaintingSpec.defaults(), new byte[0]));
    }
    public static void generate(byte[] png, PaintingSpec spec, String name, Consumer<String> callback) {
        if (busy()) { callback.accept("An upload is already active"); return; }
        if (!ClientPlayNetworking.canSend(PaintingPayload.ID)) { callback.accept("The server must install Custom Picture Frames"); return; }
        upload = png; uploadSpec = spec; uploadId = ImagePipeline.hash(UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        offset = index = 0; completion = callback; started = System.currentTimeMillis(); status = "0%";
        ClientPlayNetworking.send(new PaintingPayload(PaintingPayload.BEGIN, uploadId, 0, png.length, spec, new byte[0], PaintingNames.clean(name)));
    }
    public static void tick() {
        var client = MinecraftClient.getInstance();
        if (libraryChanged && client.world != null && client.player != null) {
            var group = net.minecraft.registry.Registries.ITEM_GROUP.get(com.example.custompictureframes.CustomPictureFramesMod.GROUP);
            if (group != null) group.updateEntries(new net.minecraft.item.ItemGroup.DisplayContext(client.world.getEnabledFeatures(), client.player.hasPermissionLevel(2), client.world.getRegistryManager()));
            libraryChanged = false;
        }
        incoming.entrySet().removeIf(e -> { if (!e.getValue().expired()) return false; TextureCache.INSTANCE.failed(e.getKey()); return true; });
        thumbnails.entrySet().removeIf(e -> { if (!e.getValue().expired()) return false; TextureCache.THUMBNAILS.failed(e.getKey()); return true; });
        if (!busy()) return;
        if (System.currentTimeMillis() - started > 120_000) { finish("Upload timed out"); return; }
        if (upload != null) {
            for (int i=0; i<2 && offset < upload.length; i++) {
                int end = Math.min(offset + Limits.CHUNK_BYTES, upload.length);
                ClientPlayNetworking.send(PaintingPayload.of(PaintingPayload.CHUNK, uploadId, index++, upload.length, uploadSpec, Arrays.copyOfRange(upload, offset, end)));
                offset = end; status = (offset * 100 / upload.length) + "%";
            }
            if (offset == upload.length) { upload = null; status = "..."; }
        }
    }
    private static void receive(PaintingPayload p) {
        try {
            switch (p.kind()) {
                case PaintingPayload.IMAGE_BEGIN, PaintingPayload.THUMB_BEGIN -> {
                    var transfers = p.kind() == PaintingPayload.THUMB_BEGIN ? thumbnails : incoming;
                    if (Limits.validId(p.id()) && transfers.size() < 8) transfers.put(p.id(), new TransferBuffer(p.total()));
                }
                case PaintingPayload.IMAGE_CHUNK, PaintingPayload.THUMB_CHUNK -> {
                    boolean thumb = p.kind() == PaintingPayload.THUMB_CHUNK;
                    var transfers = thumb ? thumbnails : incoming;
                    var buffer = transfers.get(p.id()); if (buffer == null) return;
                    buffer.append(p.index(), p.data());
                    if (buffer.complete()) { transfers.remove(p.id()); (thumb ? TextureCache.THUMBNAILS : TextureCache.INSTANCE).accept(p.id(), buffer.bytes(), true); }
                }
                case PaintingPayload.CATALOG -> {
                    if (Limits.validId(p.id()) && (LIBRARY.containsKey(p.id()) || LIBRARY.size() < 256)) {
                        LIBRARY.put(p.id(), PaintingItem.stack(p.id(), p.spec().width(), p.spec().height(), p.message()));
                        libraryChanged = true;
                    }
                }
                case PaintingPayload.RESULT -> {
                    if (p.id().isEmpty() || (p.message().isEmpty() && busy())) finish(p.message());
                    else if (!p.message().isEmpty()) { TextureCache.INSTANCE.failed(p.id()); TextureCache.THUMBNAILS.failed(p.id()); }
                }
            }
        } catch (RuntimeException e) { incoming.remove(p.id()); thumbnails.remove(p.id()); TextureCache.INSTANCE.failed(p.id()); TextureCache.THUMBNAILS.failed(p.id()); }
    }
    private static void finish(String message) {
        Consumer<String> callback = completion;
        completion = null; uploadId = null; upload = null; status = "";
        if (callback != null) callback.accept(message);
        var player = MinecraftClient.getInstance().player;
        if (player != null) player.sendMessage(message.isEmpty() ? Text.translatable("painting.created") : Text.literal(message), false);
    }
    private static void reset() {
        if (completion != null) completion.accept("Disconnected");
        incoming.clear(); thumbnails.clear(); LIBRARY.clear(); libraryChanged = true; upload = null; uploadId = null; completion = null; status = "";
    }
}
