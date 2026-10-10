// TEST ADAPTER ONLY: immutable Minecraft BakedQuad data, no Forge/GL emulation.
package net.minecraft.client.renderer.block.model;
public class BakedQuad {
 private final int[] vertices;private final int tint;private final net.minecraft.core.Direction direction;
 private final net.minecraft.client.renderer.texture.TextureAtlasSprite sprite;private final boolean shade;
 public BakedQuad(int[] v,int tint,net.minecraft.core.Direction d,net.minecraft.client.renderer.texture.TextureAtlasSprite s,boolean shade){vertices=v;this.tint=tint;direction=d;sprite=s;this.shade=shade;}
 public int[] getVertices(){return vertices;}public int getTintIndex(){return tint;}public net.minecraft.core.Direction getDirection(){return direction;}
 public net.minecraft.client.renderer.texture.TextureAtlasSprite getSprite(){return sprite;}public boolean isShade(){return shade;}
}
