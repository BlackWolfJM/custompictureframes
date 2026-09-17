package com.example.custompictureframes.client.image;

import com.example.custompictureframes.image.PixelColors;
import net.minecraft.client.texture.NativeImage;
import java.awt.image.BufferedImage;

public final class NativeImages {
    private NativeImages() {}
    /** Avoid NativeImage.read(byte[]): in 1.21.1 it copies the whole PNG onto MemoryStack. */
    public static NativeImage fromBuffered(BufferedImage source) {
        NativeImage image = new NativeImage(source.getWidth(), source.getHeight(), false);
        try {
            int[] row = new int[source.getWidth()];
            for (int y = 0; y < source.getHeight(); y++) {
                source.getRGB(0, y, source.getWidth(), 1, row, 0, source.getWidth());
                for (int x = 0; x < row.length; x++) image.setColor(x, y, PixelColors.argbToAbgr(row[x]));
            }
            return image;
        } catch (RuntimeException | Error e) { image.close(); throw e; }
    }
}
