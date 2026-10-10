package com.sora.splineroads.world;

import com.sora.splineroads.core.RoadPlanningBudget;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Server-thread read set for cached structure/sidewalk planning. World-write checks are
 * independently rerun at commit. Neighbors cover terrain collision-shape dependencies. */
final class RoadPlanInputs implements AutoCloseable {
  private static final ThreadLocal<RoadPlanInputs> CURRENT=new ThreadLocal<>();
  private final RoadPlanInputs previous;
  private final ServerLevel level;
  private final boolean recording;
  private final Map<Long,BlockState> states=new Long2ObjectOpenHashMap<>();
  private final Set<Long> probes=new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
  private final Map<Long,CompoundTag> nodes=new HashMap<>();
  private RoadPlanInputs(ServerLevel level,boolean recording){
    this.level=level;this.recording=recording;previous=CURRENT.get();CURRENT.set(this);
  }
  static RoadPlanInputs open(ServerLevel level,boolean recording){return new RoadPlanInputs(level,recording);}
  static BlockState state(ServerLevel level,BlockPos pos){
    var state=level.getBlockState(pos);var scope=CURRENT.get();
    if(scope!=null&&scope.recording&&scope.level==level&&scope.probes.add(pos.asLong())){
      // Save the actual states, not hashes: different terrain cannot alias a valid plan.
      for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
        var p=pos.offset(dx,dy,dz);
        if(!level.isOutsideBuildHeight(p))scope.states.putIfAbsent(p.asLong(),level.getBlockState(p));
      }
    }
    return state;
  }
  static BlockEntity node(ServerLevel level,BlockPos pos){
    var entity=level.getBlockEntity(pos);var scope=CURRENT.get();
    if(scope!=null&&scope.recording&&scope.level==level&&!scope.nodes.containsKey(pos.asLong()))
      scope.nodes.put(pos.asLong(),entity==null?null:entity.saveWithFullMetadata());
    return entity;
  }
  void validate(ServerLevel current){
    if(current!=level)throw new IllegalArgumentException("预览世界已改变，请重新预览");
    var chunks=new HashSet<Long>();for(long key:states.keySet())chunks.add(new net.minecraft.world.level.ChunkPos(BlockPos.of(key)).toLong());
    try(var lease=RoadWorkChunks.open(level)){
      lease.load(chunks);
      for(var entry:states.entrySet()){
        RoadPlanningBudget.check();
        if(!level.getBlockState(BlockPos.of(entry.getKey())).equals(entry.getValue()))
          throw new IllegalArgumentException("预览后地形已改变，请重新预览");
      }
      for(var entry:nodes.entrySet()){
        var entity=level.getBlockEntity(BlockPos.of(entry.getKey()));
        if(!Objects.equals(entry.getValue(),entity==null?null:entity.saveWithFullMetadata()))
          throw new IllegalArgumentException("预览后端点已改变，请重新预览");
      }
    }
  }
  @Override public void close(){if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
}
