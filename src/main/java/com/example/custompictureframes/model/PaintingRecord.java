package com.example.custompictureframes.model;

public record PaintingRecord(String id, String sourceId, PaintingSpec spec, int pixelWidth, int pixelHeight, String name) {
    public PaintingRecord { name = PaintingNames.clean(name); }
}
