package com.example.custompictureframes.client.render;

import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public final class PaintingMesh {
    private static final Identifier WOOD = Identifier.ofVanilla("textures/block/oak_planks.png");
    private PaintingMesh() {}
    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, Identifier image, float width, float height, int light, int overlay) {
        var entry = matrices.peek();
        var wood = consumers.getBuffer(RenderLayer.getEntitySolid(WOOD));
        float x=width/2, y=height/2, z=.03125f;
        // Back, sides, and a front border. Front photograph retains its original aspect ratio.
        quad(entry,wood,-x,-y,x,y,-z,0,0,1,1,light,overlay,true);
        quad(entry,wood,-x,-y,x,-y+height*.03f,z,0,0,1,1,light,overlay,false);
        quad(entry,wood,-x,y-height*.03f,x,y,z,0,0,1,1,light,overlay,false);
        quad(entry,wood,-x,-y,-x+width*.03f,y,z,0,0,1,1,light,overlay,false);
        quad(entry,wood,x-width*.03f,-y,x,y,z,0,0,1,1,light,overlay,false);
        side(entry,wood,-x,-y,-z,x,-y,-z,x,-y,z,-x,-y,z,0,-1,0,light,overlay);
        side(entry,wood,-x,y,z,x,y,z,x,y,-z,-x,y,-z,0,1,0,light,overlay);
        side(entry,wood,-x,-y,z,-x,y,z,-x,y,-z,-x,-y,-z,-1,0,0,light,overlay);
        side(entry,wood,x,-y,-z,x,y,-z,x,y,z,x,-y,z,1,0,0,light,overlay);
        Identifier front=image==null ? WOOD : image;
        quad(entry,consumers.getBuffer(RenderLayer.getEntityTranslucent(front)),-x*.94f,-y*.94f,x*.94f,y*.94f,z+.001f,0,0,1,1,light,overlay,false);
    }
    private static void quad(MatrixStack.Entry m,VertexConsumer v,float x1,float y1,float x2,float y2,float z,float u1,float v1,float u2,float v2,int light,int overlay,boolean back) {
        if(back) {
            vertex(m,v,x2,y1,z,u2,v2,0,0,-1,light,overlay); vertex(m,v,x1,y1,z,u1,v2,0,0,-1,light,overlay);
            vertex(m,v,x1,y2,z,u1,v1,0,0,-1,light,overlay); vertex(m,v,x2,y2,z,u2,v1,0,0,-1,light,overlay);
        } else {
            vertex(m,v,x1,y1,z,u1,v2,0,0,1,light,overlay); vertex(m,v,x2,y1,z,u2,v2,0,0,1,light,overlay);
            vertex(m,v,x2,y2,z,u2,v1,0,0,1,light,overlay); vertex(m,v,x1,y2,z,u1,v1,0,0,1,light,overlay);
        }
    }
    private static void side(MatrixStack.Entry m,VertexConsumer v,float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,float nx,float ny,float nz,int l,int o) {
        vertex(m,v,ax,ay,az,0,0,nx,ny,nz,l,o); vertex(m,v,bx,by,bz,1,0,nx,ny,nz,l,o);
        vertex(m,v,cx,cy,cz,1,1,nx,ny,nz,l,o); vertex(m,v,dx,dy,dz,0,1,nx,ny,nz,l,o);
    }
    private static void vertex(MatrixStack.Entry m,VertexConsumer v,float x,float y,float z,float u,float t,float nx,float ny,float nz,int light,int overlay) {
        v.vertex(m,x,y,z).color(0xffffffff).texture(u,t).overlay(overlay).light(light).normal(m,nx,ny,nz);
    }
}
