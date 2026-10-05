package net.minecraft.world.level;
public record ChunkPos(int x,int z) {
 public ChunkPos(net.minecraft.core.BlockPos p){this(p.getX()>>4,p.getZ()>>4);}
 public static long asLong(int x,int z){return (x&0xffffffffL)|((z&0xffffffffL)<<32);}public long toLong(){return asLong(x,z);}
}
