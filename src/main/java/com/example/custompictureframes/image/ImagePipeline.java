package com.example.custompictureframes.image;

import com.example.custompictureframes.model.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

public final class ImagePipeline {
    private ImagePipeline() {}
    public record Crop(double x, double y, double width, double height) {}
    public static BufferedImage decode(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > Limits.MAX_BYTES) throw new IOException("Image exceeds 8 MiB");
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("Unsupported image format (PNG/JPEG; WEBP requires a decoder)");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || w > Limits.SOURCE_SIDE || h > Limits.SOURCE_SIDE
                        || (long)w * h > Limits.SOURCE_PIXELS) throw new IOException("Image exceeds 4096 px / 16 megapixels");
                BufferedImage image = reader.read(0);
                if (image == null) throw new IOException("Invalid image");
                return image;
            } finally { reader.dispose(); }
        }
    }
    public static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "PNG", out)) throw new IOException("PNG encoder unavailable");
        return out.toByteArray();
    }
    public static Crop crop(int w, int h, PaintingSpec s) {
        double cw, ch;
        if (s.stretch()) { cw = w / s.zoom(); ch = h / s.zoom(); }
        else {
            double scale = Math.max((double)s.width() / w, (double)s.height() / h) * s.zoom();
            cw = s.width() / scale; ch = s.height() / scale;
        }
        return new Crop((w - cw) * (s.panX() + 1) / 2, (h - ch) * (s.panY() + 1) / 2, cw, ch);
    }
    public static BufferedImage render(BufferedImage source, PaintingSpec s, int maxSide) {
        Crop crop = crop(source.getWidth(), source.getHeight(), s);
        double ppb = Math.min(Limits.PIXELS_PER_BLOCK, (double)maxSide / Math.max(s.width(), s.height()));
        ppb = Math.min(ppb, Math.min(crop.width() / s.width(), crop.height() / s.height()));
        int w = Math.max(1, (int)Math.round(s.width() * ppb));
        int h = Math.max(1, (int)Math.round(s.height() * ppb));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.scale(w / crop.width(), h / crop.height());
        g.translate(-crop.x(), -crop.y());
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return out;
    }
    public static BufferedImage resize(BufferedImage source, int maxSide) {
        double scale = Math.min(1, (double)maxSide / Math.max(source.getWidth(), source.getHeight()));
        int w = Math.max(1, (int)Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int)Math.round(source.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, 0, 0, w, h, null); g.dispose();
        return out;
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public static String paintingId(String sourceId, PaintingSpec spec) {
        return hash((sourceId + ":" + spec.canonical()).getBytes(StandardCharsets.UTF_8));
    }
}
