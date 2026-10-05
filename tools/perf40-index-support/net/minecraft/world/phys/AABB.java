package net.minecraft.world.phys;
/** Test envelope adapter. */
public record AABB(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
 public AABB inflate(double n){return new AABB(minX-n,minY-n,minZ-n,maxX+n,maxY+n,maxZ+n);}
 public AABB minmax(AABB b){return new AABB(Math.min(minX,b.minX),Math.min(minY,b.minY),Math.min(minZ,b.minZ),Math.max(maxX,b.maxX),Math.max(maxY,b.maxY),Math.max(maxZ,b.maxZ));}
}
