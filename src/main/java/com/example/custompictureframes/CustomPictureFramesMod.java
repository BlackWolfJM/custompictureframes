package com.example.custompictureframes;

import com.example.custompictureframes.entity.CustomPaintingEntity;
import com.example.custompictureframes.item.PaintingItem;
import com.example.custompictureframes.network.ServerNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.*;

public final class CustomPictureFramesMod implements ModInitializer {
    public static final String MOD_ID = "custompictureframes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Item EDITOR = new Item(new Item.Settings().maxCount(1));
    public static final PaintingItem PAINTING = new PaintingItem(new Item.Settings().maxCount(16));
    public static final EntityType<CustomPaintingEntity> PAINTING_ENTITY = EntityType.Builder
            .<CustomPaintingEntity>create(CustomPaintingEntity::new, SpawnGroup.MISC)
            .dimensions(0.5f, 0.5f).maxTrackingRange(10).trackingTickInterval(Integer.MAX_VALUE)
            .build(MOD_ID + ":painting");
    public static final RegistryKey<ItemGroup> GROUP = RegistryKey.of(RegistryKeys.ITEM_GROUP, id("paintings"));
    public static Identifier id(String path) { return Identifier.of(MOD_ID, path); }
    @Override public void onInitialize() {
        Registry.register(Registries.ITEM, id("editor"), EDITOR);
        Registry.register(Registries.ITEM, id("painting"), PAINTING);
        Registry.register(Registries.ENTITY_TYPE, id("painting"), PAINTING_ENTITY);
        Registry.register(Registries.ITEM_GROUP, GROUP, FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.custompictureframes"))
                .icon(() -> new ItemStack(EDITOR)).entries((context, entries) -> entries.add(EDITOR)).build());
        ServerNetworking.register();
    }
}
