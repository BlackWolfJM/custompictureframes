package com.example.custompictureframes;
import com.example.custompictureframes.network.TransferBuffer;
import com.example.custompictureframes.model.Limits;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TransferBufferTest {
    @Test void reassemblesAndRequiresCompletion() {
        var b=new TransferBuffer(5); b.append(0,new byte[]{1,2});
        assertFalse(b.complete()); assertThrows(IllegalStateException.class,b::bytes);
        b.append(1,new byte[]{3,4,5}); assertTrue(b.complete());
        assertArrayEquals(new byte[]{1,2,3,4,5},b.bytes());
    }
    @Test void rejectsOutOfOrderDuplicateOverflowAndEmptyChunks() {
        var b=new TransferBuffer(5);
        assertThrows(IllegalArgumentException.class,()->b.append(1,new byte[]{1}));
        assertThrows(IllegalArgumentException.class,()->b.append(0,new byte[0]));
        b.append(0,new byte[]{1});
        assertThrows(IllegalArgumentException.class,()->b.append(0,new byte[]{2}));
        assertThrows(IllegalArgumentException.class,()->b.append(1,new byte[5]));
        assertThrows(IllegalArgumentException.class,()->b.append(1,new byte[Limits.CHUNK_BYTES+1]));
    }
    @Test void rejectsUnboundedAllocation() {
        assertThrows(IllegalArgumentException.class,()->new TransferBuffer(-1));
        assertThrows(IllegalArgumentException.class,()->new TransferBuffer(0));
        assertThrows(IllegalArgumentException.class,()->new TransferBuffer(Limits.MAX_BYTES+1));
    }
}
