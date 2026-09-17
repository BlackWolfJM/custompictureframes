package com.example.custompictureframes;

import com.example.custompictureframes.entity.CustomPaintingEntity;
import com.example.custompictureframes.image.ImagePipeline;
import com.example.custompictureframes.item.PaintingItem;
import com.example.custompictureframes.model.PaintingSpec;
import com.example.custompictureframes.network.ServerNetworking;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.awt.image.BufferedImage;

public class PaintingGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rectangularPaintingFitsWallAndSurvivesSave(TestContext context) {
        var world = context.getWorld();
        for (int x=1; x<=5; x++) for (int y=1; y<=8; y++)
            world.setBlockState(context.getAbsolutePos(new BlockPos(x,y,0)), Blocks.STONE.getDefaultState());
        var painting = new CustomPaintingEntity(CustomPictureFramesMod.PAINTING_ENTITY,world);
        var anchor=context.getAbsolutePos(new BlockPos(3,4,1));
        painting.configure(anchor,Direction.SOUTH,"a".repeat(64),5,8);
        painting.setCustomName(Text.literal("Mi paisaje 5x8"));
        context.assertTrue(painting.canStayAttached(),"5x8 painting must attach to a matching wall");
        context.assertTrue(painting.getBoundingBox().getLengthX()==5 && painting.getBoundingBox().getLengthY()==8,"Physical dimensions must match 5x8");
        var nbt=new NbtCompound(); painting.writeNbt(nbt);
        var restored=new CustomPaintingEntity(CustomPictureFramesMod.PAINTING_ENTITY,world); restored.readNbt(nbt);
        context.assertTrue(restored.canStayAttached(),"Restored anchor must still fit wall");
        context.assertTrue(restored.widthBlocks()==5 && restored.heightBlocks()==8,"Dimensions must survive NBT");
        context.assertTrue("Mi paisaje 5x8".equals(restored.getPickBlockStack().getName().getString()),"Name must survive save and pick block");
        context.assertTrue(PaintingItem.id(restored.getPickBlockStack()).equals("a".repeat(64)),"Image identity must survive recovery");
        world.spawnEntity(restored);
        restored.onBreak(null);
        var drops=world.getEntitiesByClass(ItemEntity.class,restored.getBoundingBox().expand(1),e->true);
        context.assertTrue(drops.stream().anyMatch(e->"Mi paisaje 5x8".equals(e.getStack().getName().getString())),"Breaking must drop named painting");
        restored.discard();
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itemFindsSpaceWhenClickingBottomCorner(TestContext context) throws Exception {
        var world=context.getWorld();
        for(int x=1;x<=5;x++) for(int y=1;y<=8;y++)
            world.setBlockState(context.getAbsolutePos(new BlockPos(x,y,0)),Blocks.STONE.getDefaultState());
        var record=ServerNetworking.store(world.getServer()).create(ImagePipeline.png(new BufferedImage(50,80,BufferedImage.TYPE_INT_ARGB)),new PaintingSpec(5,8,1,0,0,false),"Corner test");
        var player=context.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND,PaintingItem.stack(record.id(),5,8,"Corner test"));
        BlockPos corner=context.getAbsolutePos(new BlockPos(1,1,0));
        var hit=new BlockHitResult(Vec3d.ofCenter(corner).add(0,0,.5),Direction.SOUTH,corner,false);
        var result=player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
        context.assertTrue(result.isAccepted(),"A corner click must find the complete 5x8 wall");
        context.assertTrue(player.getMainHandStack().isEmpty(),"Placement consumes one item in survival");
        var list=world.getEntitiesByClass(CustomPaintingEntity.class,new Box(context.getAbsolutePos(new BlockPos(0,0,0))).expand(12),e->e.imageId().equals(record.id()));
        context.assertTrue(list.size()==1,"Exactly one rectangular painting must be placed");
        context.assertTrue(list.getFirst().getCustomName().getString().equals("Corner test"),"Item name transfers to entity");
        list.getFirst().discard();
        context.complete();
    }
}
