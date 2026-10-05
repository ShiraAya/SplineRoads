// TEST ONLY: identities remain stable until another atlas is constructed.
package net.minecraft.client.renderer.texture;
public class TextureAtlas{private final java.util.Map<net.minecraft.resources.ResourceLocation,TextureAtlasSprite> sprites=new java.util.HashMap<>();public TextureAtlasSprite getSprite(net.minecraft.resources.ResourceLocation r){return sprites.computeIfAbsent(r,k->new TextureAtlasSprite());}}
