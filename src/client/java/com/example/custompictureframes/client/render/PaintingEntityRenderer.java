package com.example.custompictureframes.client.render;

import com.example.custompictureframes.entity.CustomPaintingEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public final class PaintingEntityRenderer extends EntityRenderer<CustomPaintingEntity> {
    public PaintingEntityRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override protected boolean hasLabel(CustomPaintingEntity entity) {
        return false;
    }
    @Override public void render(CustomPaintingEntity entity,float yaw,float tickDelta,MatrixStack matrices,VertexConsumerProvider vertices,int light) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getHorizontalFacing().asRotation()));
        PaintingMesh.render(matrices,vertices,TextureCache.INSTANCE.get(entity.imageId()),entity.widthBlocks(),entity.heightBlocks(),light,OverlayTexture.DEFAULT_UV);
        matrices.pop();
        super.render(entity,yaw,tickDelta,matrices,vertices,light);
    }
    @Override public Identifier getTexture(CustomPaintingEntity entity) {
        Identifier id=TextureCache.INSTANCE.get(entity.imageId());
        return id==null ? Identifier.ofVanilla("textures/block/oak_planks.png") : id;
    }
}
