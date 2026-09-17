package com.example.custompictureframes.network;

import com.example.custompictureframes.model.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PaintingPayload(int kind, String id, int index, int total, PaintingSpec spec, byte[] data, String message)
        implements CustomPayload {
    public static final int BEGIN = 0, CHUNK = 1, REQUEST = 2, IMAGE_BEGIN = 3, IMAGE_CHUNK = 4, RESULT = 5, CATALOG = 6;
    public static final int REQUEST_THUMB = 7, THUMB_BEGIN = 8, THUMB_CHUNK = 9;
    public static final Id<PaintingPayload> ID = new Id<>(Identifier.of("custompictureframes", "painting"));
    public static final PacketCodec<RegistryByteBuf, PaintingPayload> CODEC = PacketCodec.of(PaintingPayload::write, PaintingPayload::read);
    private void write(RegistryByteBuf b) {
        b.writeVarInt(kind); b.writeString(id, 64); b.writeVarInt(index); b.writeVarInt(total);
        b.writeVarInt(spec.width()); b.writeVarInt(spec.height()); b.writeDouble(spec.zoom());
        b.writeDouble(spec.panX()); b.writeDouble(spec.panY()); b.writeBoolean(spec.stretch());
        b.writeByteArray(data); b.writeString(message, 256);
    }
    private static PaintingPayload read(RegistryByteBuf b) {
        int kind = b.readVarInt(); String id = b.readString(64); int index = b.readVarInt(), total = b.readVarInt();
        PaintingSpec spec = new PaintingSpec(b.readVarInt(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readBoolean());
        return new PaintingPayload(kind, id, index, total, spec, b.readByteArray(Limits.CHUNK_BYTES), b.readString(256));
    }
    public static PaintingPayload of(int kind, String id, int index, int total, PaintingSpec spec, byte[] data) {
        return new PaintingPayload(kind, id, index, total, spec, data, "");
    }
    public static PaintingPayload result(String id, String message) {
        return new PaintingPayload(RESULT, id, 0, 0, PaintingSpec.defaults(), new byte[0], message);
    }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
