package com.example.custompictureframes.client;

import com.example.custompictureframes.CustomPictureFramesMod;
import com.example.custompictureframes.client.gui.PaintingEditorScreen;
import com.example.custompictureframes.client.render.*;
import com.example.custompictureframes.item.PaintingItem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.TypedActionResult;
import org.lwjgl.glfw.GLFW;

public final class CustomPictureFramesClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientNetworking.register();
        EntityRendererRegistry.register(CustomPictureFramesMod.PAINTING_ENTITY, PaintingEntityRenderer::new);
        BuiltinItemRendererRegistry.INSTANCE.register(CustomPictureFramesMod.PAINTING,
                (stack,mode,matrices,vertices,light,overlay) -> {
                    int w=PaintingItem.width(stack), h=PaintingItem.height(stack);
                    float scale=.9f/Math.max(w,h);
                    matrices.push(); matrices.translate(.5,.5,.5); matrices.scale(scale,scale,scale);
                    PaintingMesh.render(matrices,vertices,TextureCache.THUMBNAILS.get(PaintingItem.id(stack)),w,h,light,overlay);
                    matrices.pop();
                });
        ItemGroupEvents.modifyEntriesEvent(CustomPictureFramesMod.GROUP).register(entries ->
                ClientNetworking.LIBRARY.values().forEach(entries::add));
        KeyBinding key=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.custompictureframes.editor",
                InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_K,"category.custompictureframes"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while(key.wasPressed()) if(client.world!=null && client.currentScreen==null) client.setScreen(new PaintingEditorScreen());
            ClientNetworking.tick(); TextureCache.INSTANCE.tick(); TextureCache.THUMBNAILS.tick();
        });
        UseItemCallback.EVENT.register((player,world,hand) -> {
            var stack=player.getStackInHand(hand);
            if(world.isClient && stack.isOf(CustomPictureFramesMod.EDITOR)) {
                MinecraftClient.getInstance().setScreen(new PaintingEditorScreen());
                return TypedActionResult.success(stack);
            }
            return TypedActionResult.pass(stack);
        });
    }
}
