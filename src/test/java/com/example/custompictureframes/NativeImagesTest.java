package com.example.custompictureframes;

import com.example.custompictureframes.client.image.NativeImages;
import com.example.custompictureframes.image.ImagePipeline;
import com.example.custompictureframes.image.PixelColors;
import java.awt.image.BufferedImage;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeImagesTest {
    @Test void detailedPngExceedingNativeStackConvertsWithoutStackAllocation() throws Exception {
        BufferedImage source = new BufferedImage(768, 768, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(42);
        for (int y=0; y<768; y++) for (int x=0; x<768; x++) source.setRGB(x,y,random.nextInt());
        assertTrue(ImagePipeline.png(source).length > 1024 * 1024);
        for (int attempt=0; attempt<3; attempt++) {
            try (var nativeImage = NativeImages.fromBuffered(source)) {
                assertEquals(768, nativeImage.getWidth());
                assertEquals(PixelColors.argbToAbgr(source.getRGB(500,600)), nativeImage.getColor(500,600));
            }
        }
    }
    @Test void nativeConversionPreservesRedBlueAndTransparency() {
        BufferedImage source = new BufferedImage(3,1,BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0,0,0xffff0000); source.setRGB(1,0,0xff0000ff); source.setRGB(2,0,0x8000ff00);
        try (var image = NativeImages.fromBuffered(source)) {
            assertEquals(0xff0000ff,image.getColor(0,0));
            assertEquals(0xffff0000,image.getColor(1,0));
            assertEquals(0x8000ff00,image.getColor(2,0));
        }
    }
}
