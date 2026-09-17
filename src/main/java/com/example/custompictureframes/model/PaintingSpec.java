package com.example.custompictureframes.model;

import java.util.Locale;

public record PaintingSpec(int width, int height, double zoom, double panX, double panY, boolean stretch) {
    public PaintingSpec {
        if (width < 1 || width > Limits.MAX_BLOCKS || height < 1 || height > Limits.MAX_BLOCKS
                || !Double.isFinite(zoom) || zoom < 1 || zoom > 8
                || !Double.isFinite(panX) || Math.abs(panX) > 1
                || !Double.isFinite(panY) || Math.abs(panY) > 1)
            throw new IllegalArgumentException("Invalid painting dimensions or crop");
    }
    public String canonical() {
        return String.format(Locale.ROOT, "%d,%d,%s,%s,%s,%s", width, height,
                Double.toHexString(zoom), Double.toHexString(panX), Double.toHexString(panY), stretch);
    }
    public static PaintingSpec defaults() { return new PaintingSpec(1, 1, 1, 0, 0, false); }
}
