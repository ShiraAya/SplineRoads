package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
/** Keep the first ramp in the real world while building and removing a sibling. */
@GameTestHolder("splineroads_matrix434") @PrefixGameTestTemplate(false)
public final class Sequential436GameTests {
 @GameTest(batch="splineroads_matrix434",template="empty",templateNamespace="splineroads_matrix434",timeoutTicks=12000)
 public static void keepSavedSibling(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);int cx=150000,cz=150000;
  for(int x=cx-320;x<=cx+20;x++)for(int z=cz-20;z<=cz+1020;z++){
   var p=new BlockPos(x,198,z);level.getChunkAt(p);level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);
  }
  var settings=RoadLanes.configure(Revision32GameTests.road(Style.O3_ONE,Structure.AUTO),RoadProfile.Type.ORDINARY,new RoadLanes.Counts(3,0),4);
  settings=settings.options(settings.options().route(settings.options().routing().fit(false)));
  var main=data.connect(level,null,Revision32GameTests.marker(h,cx,208,cz,0),Revision32GameTests.marker(h,cx,208,cz+1000,0),settings,null);
  var one=RoadLanes.configure(settings,RoadProfile.Type.ORDINARY,new RoadLanes.Counts(1,0),4);
  var sourceA=data.connect(level,null,Revision32GameTests.marker(h,cx-300,200,cz+100,-90),Revision32GameTests.marker(h,cx-220,200,cz+100,-90),one,null);
  var sourceB=data.connect(level,null,Revision32GameTests.marker(h,cx-300,200,cz+600,-90),Revision32GameTests.marker(h,cx-220,200,cz+600,-90),one,null);
  var a=Build429GameTests.point(data,sourceA,40,0);var b=Build429GameTests.point(data,main,300,0);
  var c=Build429GameTests.point(data,sourceB,40,0);var d=Build429GameTests.point(data,main,800,0);
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  var first=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),main.owner(),new LanePoints.Link(a,b,options,null));LaneRamps.build(data,level,null,first);
  var saved=data.index.roads.get(first.id());var cells=new HashMap<>(saved.cells);var blocks=new HashMap<Long,net.minecraft.world.level.block.state.BlockState>();
  cells.keySet().forEach(key->blocks.put(key,level.getBlockState(BlockPos.of(key))));
  var second=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),main.owner(),new LanePoints.Link(c,d,options,null));LaneRamps.build(data,level,null,second);
  var after=data.index.roads.get(first.id());
  h.assertTrue(after.record.alignment().equals(saved.record.alignment()),"new sibling replanned first ramp");
  h.assertTrue(after.record.structures().equals(saved.record.structures()),"new noncontact sibling changed first ramp structures");
  h.assertTrue(after.cells.equals(cells),"new sibling changed first ramp collision footprint");
  for(var entry:blocks.entrySet())h.assertTrue(level.getBlockState(BlockPos.of(entry.getKey())).equals(entry.getValue()),"new sibling replaced an old ramp block");
  var secondCells=new HashSet<>(data.index.roads.get(second.id()).cells.keySet());
  data.remove(level,null,second.id());
  h.assertTrue(data.index.roads.get(first.id()).record.save().equals(saved.record.save()),"deleting sibling changed saved first ramp");
  for(long key:secondCells)if(!data.index.occupied(key))h.assertTrue(!RoadBlocks.isCollider(level.getBlockState(BlockPos.of(key))),"deleted sibling left orphan collision block");
  var reload=RoadData.load(data.save(new CompoundTag()));
  h.assertTrue(reload.index.roads.get(first.id()).record.save().equals(saved.record.save()),"save/load changed first ramp");
  System.out.println("SEQUENTIAL436 REAL_WORLD PASS: add two shared-host ramps, first alignment/structures/cells/blocks unchanged, remove second, no orphan colliders, NBT reload");
  h.succeed();
 }
}
