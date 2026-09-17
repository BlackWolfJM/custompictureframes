package com.example.custompictureframes.network;

import com.example.custompictureframes.model.Limits;
import java.io.ByteArrayOutputStream;

public final class TransferBuffer {
    private final int expected;
    private final ByteArrayOutputStream data;
    private int next;
    private long touched = System.currentTimeMillis();
    public TransferBuffer(int expected) {
        if (expected < 1 || expected > Limits.MAX_BYTES) throw new IllegalArgumentException("Invalid transfer size");
        this.expected = expected; data = new ByteArrayOutputStream(Math.min(expected, Limits.CHUNK_BYTES));
    }
    public void append(int index, byte[] chunk) {
        if (expired() || index != next || chunk.length == 0 || chunk.length > Limits.CHUNK_BYTES
                || data.size() + chunk.length > expected) throw new IllegalArgumentException("Invalid transfer fragment");
        data.writeBytes(chunk); next++; touched = System.currentTimeMillis();
    }
    public boolean complete() { return data.size() == expected; }
    public boolean expired() { return System.currentTimeMillis() - touched > Limits.TRANSFER_TIMEOUT_MS; }
    public byte[] bytes() {
        if (!complete()) throw new IllegalStateException("Incomplete transfer");
        return data.toByteArray();
    }
}
