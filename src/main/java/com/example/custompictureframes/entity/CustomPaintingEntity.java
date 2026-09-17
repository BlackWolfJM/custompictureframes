package com.example.custompictureframes.entity;

import com.example.custompictureframes.item.PaintingItem;
import com.example.custompictureframes.model.Limits;
import net.minecraft.entity.*;
import net.minecraft.entity.data.*;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

public final class CustomPaintingEntity extends AbstractDecorationEntity {
    private static final TrackedData<String> IMAGE = DataTracker.registerData(CustomPaintingEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> WIDTH = DataTracker.registerData(CustomPaintingEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> HEIGHT = DataTracker.registerData(CustomPaintingEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> FACE = DataTracker.registerData(CustomPaintingEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<BlockPos> ANCHOR = DataTracker.registerData(CustomPaintingEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    public CustomPaintingEntity(EntityType<? extends CustomPaintingEntity> type, World world) { super(type, world); }
    @Override protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(IMAGE, ""); builder.add(WIDTH, 1); builder.add(HEIGHT, 1);
        builder.add(FACE, Direction.NORTH.getId()); builder.add(ANCHOR, BlockPos.ORIGIN);
    }
    public String imageId() { return dataTracker.get(IMAGE); }
    public int widthBlocks() { return dataTracker.get(WIDTH); }
    public int heightBlocks() { return dataTracker.get(HEIGHT); }
    public void configure(BlockPos pos, Direction side, String id, int w, int h) {
        if (!Limits.validId(id) || side.getAxis().isVertical()) throw new IllegalArgumentException("Invalid painting");
        dataTracker.set(IMAGE, id); dataTracker.set(WIDTH, Math.clamp(w, 1, Limits.MAX_BLOCKS));
        dataTracker.set(HEIGHT, Math.clamp(h, 1, Limits.MAX_BLOCKS));
        dataTracker.set(ANCHOR, pos.toImmutable()); dataTracker.set(FACE, side.getId());
        attachedBlockPos = pos.toImmutable(); setFacing(side);
    }
    @Override public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (WIDTH.equals(data) || HEIGHT.equals(data) || FACE.equals(data) || ANCHOR.equals(data)) {
            attachedBlockPos = dataTracker.get(ANCHOR);
            Direction side = Direction.byId(dataTracker.get(FACE));
            setFacing(side.getAxis().isHorizontal() ? side : Direction.NORTH);
        }
    }
    @Override protected Box calculateBoundingBox(BlockPos pos, Direction side) {
        int w = widthBlocks(), h = heightBlocks();
        Vec3d center = Vec3d.ofCenter(pos).subtract(side.getOffsetX() * 0.46875, 0, side.getOffsetZ() * 0.46875);
        Direction lateral = side.rotateYCounterclockwise();
        center = center.add(lateral.getOffsetX() * (w % 2 == 0 ? 0.5 : 0),
                h % 2 == 0 ? 0.5 : 0, lateral.getOffsetZ() * (w % 2 == 0 ? 0.5 : 0));
        double x = side.getAxis() == Direction.Axis.X ? 0.0625 : w;
        double z = side.getAxis() == Direction.Axis.Z ? 0.0625 : w;
        return Box.of(center, x, h, z);
    }
    @Override public void writeCustomDataToNbt(NbtCompound n) {
        super.writeCustomDataToNbt(n);
        n.putString("PaintingId", imageId()); n.putInt("Width", widthBlocks()); n.putInt("Height", heightBlocks());
        n.putInt("Facing", facing.getId());
    }
    @Override public void readCustomDataFromNbt(NbtCompound n) {
        super.readCustomDataFromNbt(n);
        String id = n.getString("PaintingId");
        if (!Limits.validId(id)) { discard(); return; }
        Direction side = Direction.byId(n.getInt("Facing"));
        configure(attachedBlockPos, side.getAxis().isHorizontal() ? side : Direction.NORTH, id, n.getInt("Width"), n.getInt("Height"));
    }
    @Override public void onPlace() { playSound(SoundEvents.ENTITY_PAINTING_PLACE, 1, 1); }
    @Override public Vec3d getSyncedPos() { return Vec3d.of(attachedBlockPos); }
    @Override public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> createSpawnPacket(net.minecraft.server.network.EntityTrackerEntry entry) {
        return new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(this, facing.getId(), attachedBlockPos);
    }
    @Override public void onSpawnPacket(net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet); setFacing(Direction.byId(packet.getEntityData()));
    }
    @Override public void refreshPositionAndAngles(double x, double y, double z, float yaw, float pitch) { setPosition(x, y, z); }
    @Override public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps) { setPosition(x, y, z); }
    @Override public void onBreak(Entity breaker) {
        playSound(SoundEvents.ENTITY_PAINTING_BREAK, 1, 1);
        if (!getWorld().isClient && getWorld().getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)
                && !(breaker instanceof PlayerEntity player && player.isCreative()))
            dropStack(getPickBlockStack());
    }
    @Override public ItemStack getPickBlockStack() {
        ItemStack stack = PaintingItem.stack(imageId(), widthBlocks(), heightBlocks());
        if (getCustomName() != null) stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, getCustomName());
        return stack;
    }
}
