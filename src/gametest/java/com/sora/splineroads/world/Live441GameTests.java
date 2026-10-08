package com.sora.splineroads.world;
import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;import net.minecraft.gametest.framework.*;import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;import java.util.*;
@GameTestHolder("splineroads_live441") @PrefixGameTestTemplate(false)
public final class Live441GameTests {
 @GameTest(batch="splineroads_live441",template="empty",templateNamespace="splineroads_live441",timeoutTicks=18000)
 public static void bridgeToAutomatic(GameTestHelper h){scenario(h,false,210000);}
 @GameTest(batch="splineroads_live441",template="empty",templateNamespace="splineroads_live441",timeoutTicks=18000)
 public static void lowGroundArrival(GameTestHelper h){scenario(h,true,212000);}
 private static void scenario(GameTestHelper h,boolean ground,int cx){
  var level=h.getLevel();var data=RoadData.get(level);int cz=cx;
  for(int x=cx-18;x<=cx+108;x++)for(int z=cz;z<=cz+402;z++){
   var p=new BlockPos(x,ground?198:188,z);level.getChunkAt(p);level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);
  }
  var settings=RoadLanes.configure(Revision32GameTests.road(Style.O4_RAIL,ground?Structure.AUTO:Structure.BRIDGE),RoadProfile.Type.ORDINARY,new RoadLanes.Counts(2,3),4);
  settings=settings.options(settings.options().route(settings.options().routing().fit(false)).infrastructure(settings.options().infrastructure().bridge(RoadInfrastructure.Bridge.OVERPASS)));
  var source=data.connect(level,null,Revision32GameTests.marker(h,cx,200,cz,0),Revision32GameTests.marker(h,cx,200,cz+200,0),settings,null);
  var target=data.connect(level,null,Revision32GameTests.marker(h,cx+90,ground?200:208,cz+180,0),Revision32GameTests.marker(h,cx+90,ground?200:208,cz+400,0),settings,null);
  var sourceMesh=source.mesh();int slot=LaneSections.live(sourceMesh,60).lanes().stream().filter(l->l.sign()==1&&LaneSections.edge(sourceMesh,60,l.index())).findFirst().orElseThrow().index();
  var from=Build429GameTests.point(data,source,60,slot);var to=Build429GameTests.point(data,target,140,slot);
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.FLEXIBLE);
  var ramp=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),source.owner(),new LanePoints.Link(from,to,options,null));
  System.out.println("LIVE441 PLAN_PASS ground="+ground);LaneRamps.build(data,level,null,ramp);
  System.out.println("LIVE441 BUILD_PASS ground="+ground);
  if(!ground){
   ramp=data.index.roads.get(ramp.id()).record;var bridge=ramp.settings().structure(Structure.BRIDGE);bridge=bridge.options(bridge.options().infrastructure(bridge.options().infrastructure().bridge(RoadInfrastructure.Bridge.OVERPASS)));
   var edit=LaneRamps.reconfigure(ramp,bridge,LaneTopology.records(data));LaneRamps.build(data,level,null,edit);System.out.println("LIVE441 RAMP_OVERPASS_PASS");
   ramp=data.index.roads.get(ramp.id()).record;edit=LaneRamps.reconfigure(ramp,ramp.settings().structure(Structure.AUTO),LaneTopology.records(data));LaneRamps.build(data,level,null,edit);System.out.println("LIVE441 RAMP_AUTO_PASS");
   source=data.index.roads.get(source.id()).record;data.connect(level,null,source.a(),source.b(),source.settings().structure(Structure.AUTO),source.id());System.out.println("LIVE441 SWITCH_PASS");}
  else {
   var host=data.index.roads.get(target.id()).record;
   h.assertTrue(host.structures().stream().anyMatch(LaneClosureLandscape::paved),"ground arrival left former planter unpaved");
   h.assertTrue(host.structures().stream().filter(LaneClosureLandscape::paved).allMatch(p->p.a().y()+p.height()<200.1),"pavement fill rises above road");
  }
  var planning=new ArrayList<RoadIndex.Built>(data.index.roads.values());var ids=List.of(source.id(),target.id(),ramp.id());
  h.assertTrue(!LaneTopology.needsRefresh(data,planning,ids)&&!LaneCrossSections.needsRestoreRefresh(planning,ids),"saved topology is unstable");
  var saved=RoadData.load(data.save(new net.minecraft.nbt.CompoundTag()));h.assertTrue(saved.index.roads.get(ramp.id()).record.save().equals(data.index.roads.get(ramp.id()).record.save()),"NBT changed joined ramp");
  System.out.println("LIVE441 REAL_WORLD PASS ground="+ground);h.succeed();
 }
}
