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
public final class Sequential437GameTests {
 @GameTest(batch="splineroads_matrix434",template="empty",templateNamespace="splineroads_matrix434",timeoutTicks=12000)
 public static void keepNearbySibling(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);int cx=160000,cz=160000;
  for(int x=cx-320;x<=cx+20;x++)for(int z=cz-20;z<=cz+720;z++){
   var p=new BlockPos(x,198,z);level.getChunkAt(p);level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);
  }
  var settings=RoadLanes.configure(Revision32GameTests.road(Style.O3_ONE,Structure.AUTO),RoadProfile.Type.ORDINARY,new RoadLanes.Counts(3,0),4);
  settings=settings.options(settings.options().route(settings.options().routing().fit(false)));
  var main=data.connect(level,null,Revision32GameTests.marker(h,cx,208,cz,0),Revision32GameTests.marker(h,cx,208,cz+700,0),settings,null);
  var one=RoadLanes.configure(settings,RoadProfile.Type.ORDINARY,new RoadLanes.Counts(1,0),4);
  var sourceA=data.connect(level,null,Revision32GameTests.marker(h,cx-300,200,cz+100,-90),Revision32GameTests.marker(h,cx-220,200,cz+100,-90),one,null);
  var sourceB=data.connect(level,null,Revision32GameTests.marker(h,cx-300,200,cz+260,-90),Revision32GameTests.marker(h,cx-220,200,cz+260,-90),one,null);
  var a=Build429GameTests.point(data,sourceA,40,0);var b=Build429GameTests.point(data,main,300,0);
  var c=Build429GameTests.point(data,sourceB,40,0);var d=Build429GameTests.point(data,main,430,1);
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
  System.out.println("CLOSE437 REAL_WORLD PASS: add two shared-host ramps, first alignment/structures/cells/blocks unchanged, remove second, no orphan colliders, NBT reload");
  // A second edit of the near sibling must leave the original saved ramp intact.
  var edited=LaneRamps.generate(data,LaneTopology.records(data),second.id(),main.owner(),new LanePoints.Link(c,d,options,null));LaneRamps.build(data,level,null,edited);
  h.assertTrue(data.index.roads.get(first.id()).record.save().equals(saved.record.save()),"rebuilding sibling changed original ramp");
  data.remove(level,null,edited.id());
  h.succeed();
 }
 @GameTest(batch="splineroads_matrix434",template="empty",templateNamespace="splineroads_matrix434",timeoutTicks=12000)
 public static void outerExpansionRoundTrip(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);int cx=170000,cz=170000;
  for(int x=cx-20;x<=cx+20;x++)for(int z=cz-2;z<=cz+302;z++){var p=new BlockPos(x,198,z);level.getChunkAt(p);level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);}
  var settings=RoadLanes.configure(Revision32GameTests.road(Style.O3_ONE,Structure.AUTO),RoadProfile.Type.ORDINARY,new RoadLanes.Counts(3,0),4);
  var road=data.connect(level,null,Revision32GameTests.marker(h,cx,200,cz,0),Revision32GameTests.marker(h,cx,200,cz+300,0),settings,null);
  var p=LanePointTool.create(level,null,road.id(),LanePoints.lane(road.mesh(),100,2).position());
  var t=new CompoundTag();t.putUUID("Id",road.id());t.putUUID("Point",p.id());t.putDouble("MergeLength",-32);t.putInt("Signature",data.index.roads.get(road.id()).record.header().hashCode());
  LanePointTool.edit(level,null,t);var expanded=data.index.roads.get(road.id());
  h.assertTrue(LaneSections.live(expanded.mesh,180).forward()==4,"authored outer expansion not built");
  var reload=RoadData.load(data.save(new CompoundTag()));h.assertTrue(reload.index.roads.get(road.id()).record.save().equals(expanded.record.save()),"expansion NBT changed");
  t.putDouble("MergeLength",0);t.putInt("Signature",expanded.record.header().hashCode());LanePointTool.edit(level,null,t);
  h.assertTrue(LaneSections.live(data.index.roads.get(road.id()).mesh,180).forward()==3,"cancel expansion did not restore lane count");
  data.remove(level,null,road.id());System.out.println("EXPANSION437 REAL_WORLD PASS: blue point expands 3 to 4, Mojang NBT reload, cancel restores 3");h.succeed();
 }
}
