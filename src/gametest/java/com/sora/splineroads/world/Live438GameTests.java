package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_live438") @PrefixGameTestTemplate(false)
public final class Live438GameTests {
 @GameTest(batch="splineroads_live438",template="empty",templateNamespace="splineroads_matrix434",timeoutTicks=12000)
 public static void recheckAddedLaneAndDeleteBroken(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);int cx=180000,cz=180000;
  for(int x=cx-180;x<=cx+20;x++)for(int z=cz-2;z<=cz+702;z++){var p=new BlockPos(x,198,z);level.getChunkAt(p);level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);}
  var settings=RoadLanes.configure(Revision32GameTests.road(Style.O2_ONE,Structure.AUTO),RoadProfile.Type.ORDINARY,new RoadLanes.Counts(2,0),4);
  settings=settings.options(settings.options().route(settings.options().routing().fit(false)));
  var main=data.connect(level,null,Revision32GameTests.marker(h,cx,208,cz,0),Revision32GameTests.marker(h,cx,208,cz+700,0),settings,null);
  var one=RoadLanes.configure(settings,RoadProfile.Type.ORDINARY,new RoadLanes.Counts(1,0),4);
  var source=data.connect(level,null,Revision32GameTests.marker(h,cx-160,200,cz+100,0),Revision32GameTests.marker(h,cx-160,200,cz+300,0),one,null);
  var from=Build429GameTests.point(data,source,80,0);var to=Build429GameTests.point(data,main,480,1);
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.ADD,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.FLEXIBLE);
  var ramp=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),main.owner(),new LanePoints.Link(from,to,options,null));LaneRamps.build(data,level,null,ramp);
  for(int pass=0;pass<3;pass++){
   var old=data.index.roads.get(ramp.id()).record;
   var next=LaneRamps.generate(data,LaneTopology.records(data),old.id(),old.owner(),LaneTopology.metadata(old).link());
   h.assertTrue(next.alignment().equals(old.alignment()),"unchanged recheck regenerated saved alignment");
   var preview=data.previewAssembly(level,null,new ArrayList<>(List.of(new RoadIndex.Built(next))),new HashSet<>(Set.of(next.id())),Set.of(main.a(),main.b(),source.a(),source.b()),List.of());
   h.assertTrue(preview.stream().anyMatch(b->b.record.id().equals(next.id())),"recheck preview omitted edited ramp");
   LaneRamps.build(data,level,null,next);
   h.assertTrue(!LaneTopology.needsRefresh(data,new ArrayList<>(data.index.roads.values()),Set.of(main.id(),source.id(),ramp.id())),"saved points are still unstable");
   var loaded=RoadData.load(data.save(new CompoundTag()));
   h.assertTrue(loaded.index.roads.get(ramp.id()).record.save().equals(data.index.roads.get(ramp.id()).record.save()),"recheck changed NBT on reload");
  }
  // Simulate a loaded old-world error: a surviving saved connector intersects
  // a host when the selected ramp's reservation is restored. No new build is
  // allowed to create this geometry; deletion must still recover the world.
  var saved=data.index.roads.get(ramp.id()).record;
  var samples=saved.alignment().stream().map(s->new Sample(new V(s.center().x(),208,s.center().z()),s.left(),s.distance(),s.halfWidth())).toList();
  var faulty=new RoadRecord(UUID.randomUUID(),saved.owner(),saved.a(),saved.b(),saved.start(),saved.end(),saved.settings(),false,4).alignment(null,RoadRibbon.mesh(samples,saved.settings()));
  data.index.put(new RoadIndex.Built(faulty));
  var before=faulty.save();var oldCells=new HashSet<>(data.index.roads.get(saved.id()).cells.keySet());
  data.remove(level,null,saved.id());
  h.assertTrue(!data.index.roads.containsKey(saved.id()),"faulty saved neighbor blocked deletion");
  h.assertTrue(data.index.roads.get(faulty.id()).record.save().equals(before),"delete modified surviving broken connector");
  for(long key:oldCells)if(!data.index.occupied(key))h.assertTrue(!RoadBlocks.isCollider(level.getBlockState(BlockPos.of(key))),"deletion left orphan colliders");
  data.remove(level,null,faulty.id());
  h.assertTrue(LaneTopology.metadata(data.index.roads.get(main.id()).record).additions().isEmpty(),"deletion left added lane reservation");
  System.out.println("LIVE438 REAL_WORLD PASS: ADD preview/build rechecked 3 times, stable alignment/ports, Mojang NBT, deletion with invalid saved neighbor, no orphan colliders");h.succeed();
 }
}
