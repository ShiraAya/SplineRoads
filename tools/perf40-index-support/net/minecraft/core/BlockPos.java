package net.minecraft.core;
/** Test value adapter; same packing as 1.20.1, no world behavior. */
public record BlockPos(int x,int y,int z) {
 public static BlockPos of(long v){return new BlockPos((int)(v>>38),(int)(v<<52>>52),(int)(v<<26>>38));}
 public static long asLong(int x,int y,int z){return ((long)x&0x3FFFFFF)<<38|((long)z&0x3FFFFFF)<<12|((long)y&0xFFF);}
 public long asLong(){return asLong(x,y,z);}public int getX(){return x;}public int getY(){return y;}public int getZ(){return z;}
}
