package com.example.custompictureframes.item;

import com.example.custompictureframes.CustomPictureFramesMod;
import com.example.custompictureframes.entity.CustomPaintingEntity;
import com.example.custompictureframes.model.Limits;
import com.example.custompictureframes.model.PlacementOffsets;
import com.example.custompictureframes.network.ServerNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.*;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import java.util.List;

public final class PaintingItem extends Item {
    public PaintingItem(Settings settings) { super(settings); }
    public static ItemStack stack(String id, int w, int h) {
        return stack(id, w, h, "");
    }
    public static ItemStack stack(String id, int w, int h, String name) {
        ItemStack stack = new ItemStack(CustomPictureFramesMod.PAINTING);
        NbtCompound n = new NbtCompound(); n.putString("PaintingId", id); n.putInt("Width", w); n.putInt("Height", h);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        String clean = com.example.custompictureframes.model.PaintingNames.clean(name);
        if (!clean.isEmpty()) stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(clean));
        return stack;
    }
    public static NbtCompound data(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
    }
    public static String id(ItemStack stack) { return data(stack).getString("PaintingId"); }
    public static int width(ItemStack stack) { return Math.clamp(data(stack).getInt("Width"), 1, Limits.MAX_BLOCKS); }
    public static int height(ItemStack stack) { return Math.clamp(data(stack).getInt("Height"), 1, Limits.MAX_BLOCKS); }
    @Override public ActionResult useOnBlock(ItemUsageContext context) {
        var world = context.getWorld(); var player = context.getPlayer();
        Direction side = context.getSide();
        if (player == null || side.getAxis().isVertical()) return ActionResult.FAIL;
        BlockPos pos = context.getBlockPos().offset(side);
        if (!player.canPlaceOn(pos, side, context.getStack()) || !world.canPlayerModifyAt(player, context.getBlockPos())) return ActionResult.FAIL;
        String id = id(context.getStack());
        if (!Limits.validId(id)) return ActionResult.FAIL;
        if (world.isClient) return ActionResult.SUCCESS;
        var store = ServerNetworking.store(world.getServer());
        var record = store == null ? null : store.get(id);
        if (record == null) {
            player.sendMessage(Text.translatable("painting.error.missing"), true); return ActionResult.FAIL;
        }
        CustomPaintingEntity entity = new CustomPaintingEntity(CustomPictureFramesMod.PAINTING_ENTITY, world);
        boolean found = false;
        for (var offset : PlacementOffsets.candidates(record.spec().width(), record.spec().height())) {
            BlockPos anchor = pos.offset(side.rotateYCounterclockwise(), offset.horizontal()).up(offset.vertical());
            entity.configure(anchor, side, id, record.spec().width(), record.spec().height());
            if (!world.getWorldBorder().contains(entity.getBoundingBox())) continue;
            Box support = entity.getBoundingBox().offset(-side.getOffsetX() * 0.5, 0, -side.getOffsetZ() * 0.5).contract(0.0000001);
            boolean allowed = true;
            for (BlockPos supportPos : BlockPos.iterate(BlockPos.ofFloored(support.minX, support.minY, support.minZ),
                    BlockPos.ofFloored(support.maxX, support.maxY, support.maxZ))) {
                if (world.isOutOfHeightLimit(supportPos) || !world.isChunkLoaded(supportPos) || !world.canPlayerModifyAt(player, supportPos)) { allowed = false; break; }
            }
            if (allowed && entity.canStayAttached()) { found = true; break; }
        }
        if (!found) { player.sendMessage(Text.translatable("painting.error.wall", record.spec().width(), record.spec().height()), true); return ActionResult.FAIL; }
        entity.setCustomName(context.getStack().get(DataComponentTypes.CUSTOM_NAME));
        if (!world.spawnEntity(entity)) return ActionResult.FAIL;
        entity.onPlace(); context.getStack().decrementUnlessCreative(1, player);
        return ActionResult.CONSUME;
    }
    @Override public Text getName(ItemStack stack) {
        return Text.translatable("item.custompictureframes.painting.sized", width(stack), height(stack));
    }
    @Override public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        String id = id(stack);
        if (Limits.validId(id)) tooltip.add(Text.literal(id.substring(0, 12)).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("painting.tooltip").formatted(Formatting.GRAY));
    }
}
