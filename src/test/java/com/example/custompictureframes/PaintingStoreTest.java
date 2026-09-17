package com.example.custompictureframes;
import com.example.custompictureframes.storage.PaintingStore;
import com.example.custompictureframes.image.ImagePipeline;
import com.example.custompictureframes.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;
class PaintingStoreTest {
    @TempDir Path root;
    @Test void persistsImageAndCropAndDeduplicatesOriginals() throws Exception {
        var image=new BufferedImage(32,16,BufferedImage.TYPE_INT_ARGB); image.setRGB(4,4,0xffff0000);
        byte[] png=ImagePipeline.png(image); var store=new PaintingStore(root);
        var spec=new PaintingSpec(5,8,2,.25,-.5,false); var first=store.create(png,spec);
        assertEquals(first,store.create(png,spec));
        var second=store.create(png,new PaintingSpec(8,5,1,0,0,false));
        assertNotEquals(first.id(),second.id());
        try(var paths=Files.list(root.resolve("originals"))) { assertEquals(1,paths.count()); }
        var reopened=new PaintingStore(root); assertEquals(first,reopened.get(first.id()));
        assertArrayEquals(store.read(first.id()),reopened.read(first.id()));
        assertEquals(spec,reopened.get(first.id()).spec());
    }
    @Test void untrustedPathAndUnknownImageAreRejected() throws Exception {
        var store=new PaintingStore(root);
        assertThrows(java.io.IOException.class,()->store.read("../../secrets"));
        assertThrows(java.io.IOException.class,()->store.read("a".repeat(64)));
    }
    @Test void badManifestDoesNotPreventWorldOpening() throws Exception {
        Files.createDirectories(root.resolve("paintings")); Files.writeString(root.resolve("paintings/bad.json"),"{broken");
        assertTrue(new PaintingStore(root).all().isEmpty());
    }
    @Test void namesPersistWithoutDuplicatingImageAssets() throws Exception {
        byte[] png=ImagePipeline.png(new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB));
        var store=new PaintingStore(root);
        var first=store.create(png,PaintingSpec.defaults(),"Mi paisaje");
        assertEquals("Mi paisaje",new PaintingStore(root).get(first.id()).name());
        var renamed=store.create(png,PaintingSpec.defaults(),"Otro nombre");
        assertEquals(first.id(),renamed.id());
        assertEquals("Otro nombre",new PaintingStore(root).get(first.id()).name());
        try(var paths=Files.list(root.resolve("paintings"))) { assertEquals(1,paths.filter(p->p.toString().endsWith(".png")).count()); }
    }
}
