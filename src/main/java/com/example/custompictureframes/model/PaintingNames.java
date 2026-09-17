package com.example.custompictureframes.model;

public final class PaintingNames {
    public static final int MAX_LENGTH = 48;
    private PaintingNames() {}
    public static String clean(String name) {
        if (name == null) return "";
        StringBuilder out = new StringBuilder();
        name.strip().codePoints().filter(c -> !Character.isISOControl(c) && c != 0x00a7)
                .limit(MAX_LENGTH).forEach(out::appendCodePoint);
        return out.toString().strip();
    }
}
