package com.example.custompictureframes.model;

public final class Limits {
    private Limits() {}
    public static final int MAX_BLOCKS = 16;
    public static final int SOURCE_SIDE = 4096;
    public static final long SOURCE_PIXELS = 16_777_216L;
    public static final int TEXTURE_SIDE = 2048;
    public static final int PIXELS_PER_BLOCK = 256;
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int CHUNK_BYTES = 24 * 1024;
    public static final long TRANSFER_TIMEOUT_MS = 60_000;
    public static boolean validId(String id) { return id != null && id.matches("[a-f0-9]{64}"); }
}
