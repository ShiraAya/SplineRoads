package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_live445") @PrefixGameTestTemplate(false)
public final class Live445GameTests {
 @GameTest(batch="splineroads_live445",template="empty",templateNamespace="splineroads_live445",timeoutTicks=18000)
 public static void temporarySelfHostAndAddBuildReload(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);int x=240000,z=240000;
  for(int d=0;d<=500;d++)for(int side=-16;side<=16;side++){
   for(var p:List.of(new BlockPos(x+d,130,z+side),new BlockPos(x+250+side,130,z-250+d))){level.getChunkAt(p);level.setBlock(p,Blocks.STONE.defaultBlockState(),2);}
  }
  var sourceSettings=new Settings(Mode.STRAIGHT,Style.O6_RAIL,26,1,.35,90).options(RoadProfile.Options.DEFAULT.hideArrows(true).route(RoadProfile.Routing.DEFAULT.fit(false)));
  var targetSettings=new Settings(Mode.STRAIGHT,Style.O6_GREEN,28,1,.35,90).options(RoadProfile.Options.DEFAULT.hideArrows(true).route(RoadProfile.Routing.DEFAULT.fit(false)));
  var a=Revision32GameTests.marker(h,x,158,z,-90);var b=Revision32GameTests.marker(h,x+500,158,z,-90);
  var c=Revision32GameTests.marker(h,x+250,150,z-250,0);var d=Revision32GameTests.marker(h,x+250,150,z+250,0);
  var source=data.connect(level,null,a,b,sourceSettings,null);var target=data.connect(level,null,c,d,targetSettings,null);
  var from=Build429GameTests.point(data,source,100,5);var to=Build429GameTests.point(data,target,400,3);
  var options=new LanePoints.Options(LanePoints.Path.RIGHT,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.ADD,24,32,LanePoints.Elevation.OVER,LanePoints.Landing.FLEXIBLE);
  var ramp=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),source.owner(),new LanePoints.Link(from,to,options,null));
  h.assertTrue(ramp.settings().width()>=5.5,"missing clear four-block motor strip plus rail shoulders");
  LaneRamps.build(data,level,null,ramp);System.out.println("LIVE445 TEMPORARY_BUILD_PASS");
  var built=data.index.roads.get(ramp.id()).record;var host=data.index.roads.get(source.id()).record;var arrival=data.index.roads.get(target.id()).record;
  h.assertTrue(LaneTopology.metadata(host).cuts().stream().anyMatch(q->q.connection().equals(ramp.id())&&q.temporary()),"temporary reservation not published");
  h.assertTrue(LaneTopology.metadata(arrival).additions().stream().anyMatch(q->q.connection().equals(ramp.id())),"ADD lane not published");
  LaneRamps.validate(built.mesh(),LaneTopology.records(data),built.id(),LaneTopology.metadata(built).link());
  var reloaded=RoadData.load(data.save(new CompoundTag()));h.assertTrue(reloaded.index.roads.get(built.id()).record.alignment().equals(built.alignment()),"Mojang save/reload shifted ramp");
  var old=host.mesh();data.connect(level,null,a,b,host.settings(),host.id());var edited=data.index.roads.get(host.id()).mesh;
  h.assertTrue(old.first().center().distance(edited.first().center())<1e-6&&old.last().center().distance(edited.last().center())<1e-6,"unchanged host edit shifted its ends");
  data.remove(level,null,ramp.id());h.assertTrue(LaneTopology.metadata(data.index.roads.get(source.id()).record).cuts().isEmpty(),"temporary cut survived deletion");
  var innerFrom=Build429GameTests.point(data,source,100,4);
  var internal=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),source.owner(),new LanePoints.Link(innerFrom,to,options,null));
  LaneRamps.build(data,level,null,internal);h.assertTrue(data.index.roads.containsKey(internal.id()),"internal TEMPORARY could preview but not build");
  data.remove(level,null,internal.id());
  System.out.println("LIVE445 REAL_WORLD PASS outer/internal temporary self-host, wide ramp, ADD, ordinary edit, Mojang reload and deletion");h.succeed();
 }
}
