package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_live439") @PrefixGameTestTemplate(false)
public final class Live439GameTests {
 @GameTest(batch="splineroads_live439",template="empty",templateNamespace="splineroads_live439",timeoutTicks=12000)
 public static void terrainAndOpenMouth(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);int cx=190000,cz=190000;
  for(int x=cx-15;x<=cx+15;x++)for(int z=cz;z<=cz+302;z++){
   var pos=new BlockPos(x,198,z);level.getChunkAt(pos);level.setBlock(pos,Blocks.GRASS_BLOCK.defaultBlockState(),2);
   if(z>=cz+110&&z<=cz+170)for(int y=199;y<=203;y++)level.setBlock(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),2);
  }
  var settings=RoadLanes.configure(Revision32GameTests.road(Style.O1_ONE,Structure.AUTO),RoadProfile.Type.ORDINARY,new RoadLanes.Counts(1,0),4);
  settings=settings.options(settings.options().route(settings.options().routing().fit(false)));
  var source=data.connect(level,null,Revision32GameTests.marker(h,cx,200,cz,0),Revision32GameTests.marker(h,cx,200,cz+60,0),settings,null);
  var target=data.connect(level,null,Revision32GameTests.marker(h,cx,208,cz+240,0),Revision32GameTests.marker(h,cx,208,cz+300,0),settings,null);
  var from=Build429GameTests.point(data,source,30,0);var to=Build429GameTests.point(data,target,30,0);
  var options=new LanePoints.Options(LanePoints.Path.DIRECT,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  var ramp=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),source.owner(),new LanePoints.Link(from,to,options,null));
  // SR440: earth is excavatable; never raise/reject a route solely for terrain.
  h.assertTrue(LaneRamps.monotone(ramp.mesh()),"terrain introduced an avoidable reversal");
  h.assertTrue(level.getBlockState(new BlockPos(cx,203,cz+140)).is(Blocks.STONE),"route solve mutated terrain");
  LaneRamps.build(data,level,null,ramp);
  var built=data.index.roads.get(ramp.id()).record;
  h.assertTrue(built.alignment().equals(ramp.alignment()),"terrain changed chosen alignment");
  for(var host:List.of(source,target))for(var part:data.index.roads.get(host.id()).record.structures())
   h.assertTrue(!RoadClearance.structureInvades(part,built.mesh(),4.25),"host cap/greenery blocks ramp mouth");
  var loaded=RoadData.load(data.save(new CompoundTag()));h.assertTrue(loaded.index.roads.get(ramp.id()).record.alignment().equals(built.alignment()),"NBT lost terrain-fitted geometry");
  var cells=new HashSet<>(data.index.roads.get(ramp.id()).cells.keySet());data.remove(level,null,ramp.id());
  for(long p:cells)if(!data.index.occupied(p))h.assertTrue(!RoadBlocks.isCollider(level.getBlockState(BlockPos.of(p))),"orphan collision after delete");
  System.out.println("LIVE439 REAL_WORLD PASS: excavatable ridge, monotone route, open mouths, unblocked greenery, build, NBT and delete");h.succeed();
 }
}
