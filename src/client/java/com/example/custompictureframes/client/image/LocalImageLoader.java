package com.example.custompictureframes.client.image;

import com.example.custompictureframes.image.ImagePipeline;
import com.example.custompictureframes.model.Limits;
import javax.imageio.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;

public final class LocalImageLoader {
    public record Loaded(BufferedImage image, byte[] png, String name) {}
    public static Loaded load(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > 64L * 1024 * 1024)
            throw new IOException("File is missing or exceeds 64 MiB");
        BufferedImage image;
        try (var input = ImageIO.createImageInputStream(path.toFile())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Format not supported. Use PNG/JPEG; WEBP needs an ImageIO decoder.");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || (long)w * h > 100_000_000L) throw new IOException("Image exceeds 100 megapixels");
                var params = reader.getDefaultReadParam();
                int sample = Math.max(1, (int)Math.ceil((double)Math.max(w, h) / Limits.SOURCE_SIDE));
                params.setSourceSubsampling(sample, sample, 0, 0);
                image = reader.read(0, params);
            } finally { reader.dispose(); }
        }
        image = ImagePipeline.resize(image, Limits.SOURCE_SIDE);
        return encode(image, path.getFileName().toString());
    }
    public static Loaded rotateClockwise(Loaded source) throws IOException {
        return encode(ImagePipeline.rotateClockwise(source.image()), source.name());
    }
    private static Loaded encode(BufferedImage image, String name) throws IOException {
        byte[] png = ImagePipeline.png(image);
        while (png.length > Limits.MAX_BYTES) {
            image = ImagePipeline.resize(image, Math.max(1, (int)(Math.max(image.getWidth(), image.getHeight()) * 0.85)));
            png = ImagePipeline.png(image);
        }
        return new Loaded(image, png, name);
    }
}
