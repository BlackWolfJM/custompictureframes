package com.example.custompictureframes;
import com.example.custompictureframes.image.ImagePipeline;
import com.example.custompictureframes.model.*;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;
class ImagePipelineTest {
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
