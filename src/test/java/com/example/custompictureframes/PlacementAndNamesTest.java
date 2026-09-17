package com.example.custompictureframes;

import com.example.custompictureframes.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlacementAndNamesTest {
    @Test void allClickedCellsCanAnchorARectangularPainting() {
        for(int w=1; w<=16; w++) for(int h=1; h<=16; h++) {
            var offsets=PlacementOffsets.candidates(w,h);
            assertEquals(w*h, offsets.size());
            assertEquals(new PlacementOffsets.Offset(0,0),offsets.getFirst());
            assertEquals(w*h,offsets.stream().distinct().count());
            for(var o:offsets) {
                assertTrue(o.horizontal()-(w-1)/2<=0 && o.horizontal()+w/2>=0);
                assertTrue(o.vertical()-(h-1)/2<=0 && o.vertical()+h/2>=0);
            }
        }
    }
    @Test void userNamesAreBoundedAndPlainText() {
        assertEquals("Mi paisaje",PaintingNames.clean("  Mi paisaje  "));
        assertEquals("rojo",PaintingNames.clean("ro\n\u00a7jo"));
        assertEquals(48,PaintingNames.clean("x".repeat(100)).length());
        assertEquals("",PaintingNames.clean(null));
    }
}
