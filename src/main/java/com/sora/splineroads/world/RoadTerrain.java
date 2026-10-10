package com.sora.splineroads.world;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
/** Per-transaction original terrain, shared by route policy and structural sections. */
final class RoadTerrain {
 /** One transaction's restored-terrain column index, shared by every replanned road. */
 static final class Cache extends HashMap<BlockPos,List<AABB>> {
  final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap originalTop=new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
  Cache(Map<Long,BlockState> retained,Map<Long,BlockState> originals){
   originalTop.defaultReturnValue(Integer.MIN_VALUE);
   for(var map:List.of(retained,originals))for(var entry:map.entrySet())if(!entry.getValue().isAir()){
    var p=BlockPos.of(entry.getKey());long column=BlockPos.asLong(p.getX(),0,p.getZ());
    originalTop.put(column,Math.max(originalTop.get(column),p.getY()));
   }
  }
 }
 static RoadStructures.Ground read(ServerLevel level,Map<Long,BlockState> retained,Map<Long,BlockState> originals,Map<BlockPos,List<AABB>> terrainCache){
  return new RoadStructures.Ground(){
    public double top(double x, double z, double deckY) {
      BlockPos key = BlockPos.containing(x, Math.min(level.getMaxBuildHeight()-1,deckY), z);
      if(!RoadWorkChunks.terrainAvailable(level,key))return Double.NaN;
      var surfaces=terrainCache.computeIfAbsent(key,k->{
        var out=new ArrayList<AABB>();
        int bottom=Math.max(level.getMinBuildHeight(),key.getY()-(int)RoadStructures.MAX_DROP);
        int first=key.getY();
        if(terrainCache instanceof Cache indexed){
          // WORLD_SURFACE bounds real non-air blocks. Restored terrain can be
          // above it (a buried road cleared the old surface), so include both.
          int top=RoadPlanInputs.height(level,key.getX(),key.getZ())-1;
          top=Math.max(top,indexed.originalTop.get(BlockPos.asLong(key.getX(),0,key.getZ())));
          first=Math.min(first,top);
        }
        for(int y=first;y>=bottom;y--){
          RoadPlanningBudget.check();
          BlockPos p=new BlockPos(key.getX(),y,key.getZ());
          if(!level.hasChunkAt(p))break;
          BlockState state=RoadPlanInputs.state(level,p);
          state=RoadFoundation.source(p.asLong(),state,
              RoadBlocks.isCollider(state)||state.is(SplineRoads.TUNNEL_AIR.get()),
              state.isAir(),originals,retained,
              net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
          if(state.is(net.minecraft.tags.BlockTags.LEAVES)
              ||state.is(net.minecraft.tags.BlockTags.LOGS)
              ||state.is(SplineRoads.NODE.get())||!state.getFluidState().isEmpty())continue;
          var shape=state.getCollisionShape(level,p);
          for(var box:shape.toAabbs())out.add(box.move(p));
          // A full cube hides every lower surface in this column.
          if(y<key.getY()&&net.minecraft.world.level.block.Block.isShapeFullBlock(shape))break;
        }
        out.sort(Comparator.comparingDouble((AABB box)->box.maxY).reversed());
        return out;
      });
      for (var box : surfaces)
        if (x >= box.minX - 1e-7
            && x <= box.maxX + 1e-7
            && z >= box.minZ - 1e-7
            && z <= box.maxZ + 1e-7
            // A block beginning at the probe plane is overhead, not a foundation.
            && box.minY < deckY - 1e-7) return Math.min(box.maxY,deckY);
      return Double.NaN;
    }

    public boolean joined(V p){return false;}
    public boolean blocked(RoadStructures.Part p){return false;}
  };
 }
 private RoadTerrain(){}
}
