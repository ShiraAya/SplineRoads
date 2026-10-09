package com.sora.splineroads.world;
import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;import net.minecraft.gametest.framework.*;import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;import java.util.*;
@GameTestHolder("splineroads_live442") @PrefixGameTestTemplate(false)
public final class Live442GameTests {
 @GameTest(batch="splineroads_live442",template="empty",templateNamespace="splineroads_live441",timeoutTicks=18000)
 public static void detachContinueDeleteAndBuildAgain(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);int cx=220000,cz=cx;
  for(int x=cx-18;x<=cx+108;x++)for(int z=cz;z<=cz+522;z++){var p=new BlockPos(x,188,z);level.getChunkAt(p);level.setBlock(p,Blocks.STONE.defaultBlockState(),2);}
  var s=RoadLanes.configure(Revision32GameTests.road(Style.O4_RAIL,Structure.AUTO),RoadProfile.Type.ORDINARY,new RoadLanes.Counts(2,3),4);
  s=s.options(s.options().route(s.options().routing().fit(false)));
  var source=data.connect(level,null,Revision32GameTests.marker(h,cx,200,cz,0),Revision32GameTests.marker(h,cx,200,cz+200,0),s,null);
  var target=data.connect(level,null,Revision32GameTests.marker(h,cx+90,208,cz+180,0),Revision32GameTests.marker(h,cx+90,208,cz+520,0),s,null);
  var sm=source.mesh();int slot=LaneSections.live(sm,60).lanes().stream().filter(l->l.sign()==1&&LaneSections.edge(sm,60,l.index())).findFirst().orElseThrow().index();
  var from=Build429GameTests.point(data,source,60,slot);var to=Build429GameTests.point(data,target,160,slot);
  var temporary=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.FLEXIBLE);
  var detach=new LanePoints.Options(temporary.path(),LanePoints.Departure.DETACH,temporary.arrival(),24,32,temporary.elevation(),temporary.landing());
  var id=UUID.randomUUID();var kept=LaneRamps.generate(data,LaneTopology.records(data),id,source.owner(),new LanePoints.Link(from,to,temporary,null));
  var ramp=LaneRamps.generate(data,LaneTopology.records(data),id,source.owner(),new LanePoints.Link(from,to,detach,null));
  h.assertTrue(ramp.alignment().equals(kept.alignment()),"fresh mode switch changes route");
  LaneRamps.build(data,level,null,ramp);System.out.println("LIVE442 DETACH_BUILD_PASS");
  var host=data.index.roads.get(source.id()).record;var cut=LaneTopology.metadata(host).cuts().stream().filter(c->c.connection().equals(id)).findFirst().orElseThrow();
  h.assertTrue(cut.rectangular()&&cut.removed(60.01)>.999,"DETACH has inward merge transition");
  var inherited=RoadEndpointSections.inherit(s,RoadEndpointSections.section(host.caps(0).mesh(),false,false));
  var b=data.connect(level,null,host.b(),Revision32GameTests.marker(h,cx,200,cz+360,0),inherited,null);
  System.out.println("LIVE442 CONTINUATION_BUILD_PASS");
  assertSeam(h,data.index.roads.get(source.id()).record,data.index.roads.get(b.id()).record);
  var far=b.end();data.remove(level,null,id);System.out.println("LIVE442 DELETE_PASS");
  host=data.index.roads.get(source.id()).record;b=data.index.roads.get(b.id()).record;
  h.assertTrue(LaneSections.live(host.mesh(),160).forward()==2,"delete did not restore A to two forward lanes");
  h.assertTrue(RoadLanes.counts(b.settings()).forward()==1,"delete changed B's authored lane count");
  assertSeam(h,host,b);h.assertTrue(b.end().equals(far),"delete moved B remote endpoint");
  var again=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),source.owner(),new LanePoints.Link(from,to,detach,null));
  LaneRamps.build(data,level,null,again);System.out.println("LIVE442 REBUILD_PASS");
  assertSeam(h,data.index.roads.get(source.id()).record,data.index.roads.get(b.id()).record);
  var loaded=RoadData.load(data.save(new net.minecraft.nbt.CompoundTag()));
  h.assertTrue(loaded.index.roads.get(b.id()).record.save().equals(data.index.roads.get(b.id()).record.save()),"continuation repair changed during Mojang NBT reload");
  System.out.println("LIVE442 REAL_WORLD PASS continuation: 2->DETACH->1-lane B->delete->2-to-1 taper->new DETACH build");h.succeed();
 }
 private static void assertSeam(GameTestHelper h,RoadRecord a,RoadRecord b){
  var x=a.caps(0).mesh().last();var y=b.caps(0).mesh().first();
  h.assertTrue(x.center().distance(y.center())<1e-6,"A/B seam center mismatch: "+x.center().distance(y.center()));
  h.assertTrue(Math.abs(x.halfWidth()-y.halfWidth())<1e-6,"A/B seam width mismatch");
  var lx=RoadProfile.layout(a.caps(0).mesh(),x);var ly=RoadProfile.layout(b.caps(0).mesh(),y);
  h.assertTrue(lx.dividers().size()==ly.dividers().size(),"A/B seam divider count mismatch");
  for(int i=0;i<lx.dividers().size();i++)h.assertTrue(Math.abs(lx.dividers().get(i)-ly.dividers().get(i))<1e-5,"A/B divider offset mismatch");
 }
}
