package com.example.custompictureframes.client.image;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.nio.file.Path;

public final class LocalFilePicker {
    private LocalFilePicker() {}
    // Native dialog runs on the dedicated image worker, never on Minecraft's render thread.
    public static Path choose() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(4);
            filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg")).put(stack.UTF8("*.webp")).flip();
            String file = TinyFileDialogs.tinyfd_openFileDialog("Select local photograph", "", filters, "PNG / JPEG / WEBP", false);
            return file == null ? null : Path.of(file);
        }
    }
}
