package com.example.custompictureframes.model;

import java.util.*;

public final class PlacementOffsets {
    private PlacementOffsets() {}
    public record Offset(int horizontal, int vertical) {}
    /** All anchors for which the clicked block belongs to the painting, nearest center first. */
    public static List<Offset> candidates(int width, int height) {
        if (width < 1 || height < 1 || width > Limits.MAX_BLOCKS || height > Limits.MAX_BLOCKS)
            throw new IllegalArgumentException("Invalid wall dimensions");
        List<Offset> offsets = new ArrayList<>(width * height);
        for (int column = -(width - 1) / 2; column <= width / 2; column++)
            for (int row = -(height - 1) / 2; row <= height / 2; row++) offsets.add(new Offset(-column, -row));
        offsets.sort(Comparator.comparingInt(o -> o.horizontal() * o.horizontal() + o.vertical() * o.vertical()));
        return List.copyOf(offsets);
    }
}
