package com.example.custompictureframes.image;

public final class PixelColors {
    private PixelColors() {}
    public static int argbToAbgr(int argb) {
        return (argb & 0xff00ff00) | ((argb >>> 16) & 0xff) | ((argb & 0xff) << 16);
    }
}
