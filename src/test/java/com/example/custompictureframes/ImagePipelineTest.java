package com.example.custompictureframes;
import com.example.custompictureframes.image.ImagePipeline;
import com.example.custompictureframes.model.*;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;
class ImagePipelineTest {
    @Test void clockwiseRotationPreservesEveryPixelAndFourTurnsRestoreOriginal() {
        var source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = {0xffff0000, 0xff00ff00, 0xff0000ff, 0x80445566, 0x00778899, 0xffffffff};
        source.setRGB(0, 0, 3, 2, pixels, 0, 3);
        var rotated = ImagePipeline.rotateClockwise(source);
        assertEquals(2, rotated.getWidth()); assertEquals(3, rotated.getHeight());
        assertArrayEquals(new int[]{pixels[3], pixels[0], pixels[4], pixels[1], pixels[5], pixels[2]},
                rotated.getRGB(0, 0, 2, 3, null, 0, 2));
        for (int i = 0; i < 3; i++) rotated = ImagePipeline.rotateClockwise(rotated);
        assertEquals(3, rotated.getWidth()); assertEquals(2, rotated.getHeight());
        assertArrayEquals(pixels, rotated.getRGB(0, 0, 3, 2, null, 0, 3));
        assertArrayEquals(pixels, source.getRGB(0, 0, 3, 2, null, 0, 3));
    }
    @Test void rotatedUploadMatchesPreviewAndSurvivesServerRendering() throws Exception {
        var source = new BufferedImage(6, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 6; x++)
            source.setRGB(x, y, 0xff000000 | (x * 40 << 16) | (y * 60 << 8));
        var loaded = new com.example.custompictureframes.client.image.LocalImageLoader.Loaded(
                source, ImagePipeline.png(source), "landscape.png");
        var rotated = com.example.custompictureframes.client.image.LocalImageLoader.rotateClockwise(loaded);
        assertEquals(loaded.name(), rotated.name());
        var decoded = ImagePipeline.decode(rotated.png());
        assertEquals(4, decoded.getWidth()); assertEquals(6, decoded.getHeight());
        assertEquals(source.getRGB(0, 0), decoded.getRGB(3, 0));
        var spec = new PaintingSpec(2, 3, 1, 0, 0, false);
        var preview = ImagePipeline.render(rotated.image(), spec, 768);
        var server = ImagePipeline.render(decoded, spec, 2048);
        assertArrayEquals(preview.getRGB(0, 0, 4, 6, null, 0, 4), server.getRGB(0, 0, 4, 6, null, 0, 4));
        assertNotEquals(ImagePipeline.hash(loaded.png()), ImagePipeline.hash(rotated.png()));
    }
    @Test void cropFillsFiveByEightWithoutDistortion() {
        var crop=ImagePipeline.crop(1600,900,new PaintingSpec(5,8,1,0,0,false));
        assertEquals(5.0/8,crop.width()/crop.height(),1e-10);
        assertEquals(900,crop.height(),1e-10);
        assertEquals((1600-crop.width())/2,crop.x(),1e-10);
    }
    @Test void zoomAndPanStayInsideSource() {
        for(int w=1;w<=16;w++) for(int h=1;h<=16;h++) for(double x:new double[]{-1,0,1}) {
            var crop=ImagePipeline.crop(1920,1080,new PaintingSpec(w,h,8,x,-x,false));
            assertTrue(crop.x()>=-1e-8 && crop.y()>=-1e-8);
            assertTrue(crop.x()+crop.width()<=1920+1e-8 && crop.y()+crop.height()<=1080+1e-8);
            assertEquals((double)w/h,crop.width()/crop.height(),1e-9);
        }
    }
    @Test void stretchingKeepsWholeSourceAtUnitZoom() {
        var crop=ImagePipeline.crop(1920,1080,new PaintingSpec(5,8,1,0,0,true));
        assertEquals(1920,crop.width()); assertEquals(1080,crop.height());
    }
    @Test void pngRoundTripPreservesChannelsAndAlpha() throws Exception {
        var source=new BufferedImage(3,1,BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0,0,0xffff0000); source.setRGB(1,0,0xff0000ff); source.setRGB(2,0,0x8000ff00);
        var result=ImagePipeline.decode(ImagePipeline.png(source));
        for(int x=0;x<3;x++) assertEquals(source.getRGB(x,0),result.getRGB(x,0));
    }
    @Test void outputRespectsAspectAndResolutionLimit() {
        var source=new BufferedImage(1000,1600,BufferedImage.TYPE_INT_ARGB);
        var result=ImagePipeline.render(source,new PaintingSpec(5,8,1,0,0,false),2048);
        assertEquals(1000,result.getWidth()); assertEquals(1600,result.getHeight());
        var small=ImagePipeline.render(source,new PaintingSpec(5,8,1,0,0,false),512);
        assertEquals(320,small.getWidth()); assertEquals(512,small.getHeight());
    }
    @Test void invalidDimensionsAndNonFiniteParametersAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new PaintingSpec(0,1,1,0,0,false));
        assertThrows(IllegalArgumentException.class,()->new PaintingSpec(17,1,1,0,0,false));
        assertThrows(IllegalArgumentException.class,()->new PaintingSpec(1,1,Double.NaN,0,0,false));
        assertThrows(IllegalArgumentException.class,()->new PaintingSpec(1,1,1,Double.POSITIVE_INFINITY,0,false));
        assertThrows(IllegalArgumentException.class,()->new PaintingSpec(1,1,9,0,0,false));
    }
    @Test void imageIdentityIncludesCropAndDimensions() {
        String source=ImagePipeline.hash(new byte[]{1,2,3}); var a=new PaintingSpec(5,8,1,0,0,false);
        assertEquals(ImagePipeline.paintingId(source,a),ImagePipeline.paintingId(source,a));
        assertNotEquals(ImagePipeline.paintingId(source,a),ImagePipeline.paintingId(source,new PaintingSpec(8,5,1,0,0,false)));
        assertNotEquals(ImagePipeline.paintingId(source,a),ImagePipeline.paintingId(source,new PaintingSpec(5,8,2,0,0,false)));
    }
    @Test void oversizedHeaderRejectedBeforePixelDecode() throws Exception {
        var source=new BufferedImage(4097,1,BufferedImage.TYPE_INT_ARGB); byte[] bytes=ImagePipeline.png(source);
        assertThrows(java.io.IOException.class,()->ImagePipeline.decode(bytes));
        assertThrows(java.io.IOException.class,()->ImagePipeline.decode(new byte[]{1,2,3}));
    }
}
