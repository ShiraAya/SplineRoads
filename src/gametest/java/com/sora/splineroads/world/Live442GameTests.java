package com.sora.splineroads.world;
import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;import net.minecraft.gametest.framework.*;import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;import java.util.*;
@GameTestHolder("splineroads_live442") @PrefixGameTestTemplate(false)
public final class Live442GameTests {
 @GameTest(batch="splineroads_live442",template="empty",templateNamespace="splineroads_live442",timeoutTicks=18000)
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
  fullLane(h,source,host,slot);
  mouthRails(h,data,data.index.roads.get(id).record);
  var inherited=RoadEndpointSections.inherit(s,RoadEndpointSections.section(host.caps(0).mesh(),false,false));
  var b=data.connect(level,null,host.b(),Revision32GameTests.marker(h,cx,200,cz+360,0),inherited,null);
  System.out.println("LIVE442 CONTINUATION_BUILD_PASS");
  assertSeam(h,data.index.roads.get(source.id()).record,data.index.roads.get(b.id()).record);
  var manual=Build429GameTests.point(data,b,12,0);
  var far=b.end();data.remove(level,null,id);System.out.println("LIVE442 DELETE_PASS");
  host=data.index.roads.get(source.id()).record;b=data.index.roads.get(b.id()).record;
  h.assertTrue(LaneSections.live(host.mesh(),160).forward()==2,"delete did not restore A to two forward lanes");
  h.assertTrue(RoadLanes.counts(b.settings()).forward()==1,"delete changed B's authored lane count");
  assertSeam(h,host,b);h.assertTrue(b.end().equals(far),"delete moved B remote endpoint");
  h.assertTrue(!LaneTopology.needsRefresh(data,new ArrayList<>(data.index.roads.values()),List.of(host.id(),b.id())),"delete leaves ordinary lane points off their final lanes");
  h.assertTrue(LaneTopology.point(b,manual.point()).id().equals(manual.point()),"delete replaced continuation point identity");
  var again=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),source.owner(),new LanePoints.Link(from,to,detach,null));
  LaneRamps.build(data,level,null,again);System.out.println("LIVE442 REBUILD_PASS");
  assertSeam(h,data.index.roads.get(source.id()).record,data.index.roads.get(b.id()).record);
  var loaded=RoadData.load(data.save(new net.minecraft.nbt.CompoundTag()));
  h.assertTrue(loaded.index.roads.get(b.id()).record.save().equals(data.index.roads.get(b.id()).record.save()),"continuation repair changed during Mojang NBT reload");
  System.out.println("LIVE442 REAL_WORLD PASS continuation: 2->DETACH->1-lane B->delete->2-to-1 taper->new DETACH build");h.succeed();
 }
 private static void fullLane(GameTestHelper h,RoadRecord before,RoadRecord host,int closed){
  var original=before.rawMesh();var removed=LanePoints.lane(original,100,closed);
  var next=LaneSections.live(original,100).lanes().stream().filter(l->l.index()!=closed&&l.sign()==removed.sign()).min(Comparator.comparingDouble(l->l.position().distance(removed.position()))).orElseThrow();
  var samples=new ArrayList<Sample>();for(double d=70;d<=150;d++){var lane=LanePoints.lane(original,d,next.index());samples.add(new Sample(lane.position(),RoadStructures.sample(original,d).left(),d-70,lane.width()/2-.001));}
  var drive=RoadRibbon.mesh(samples,new Settings(Mode.STRAIGHT,Style.C1_RAMP,4,1,.35,90));
  h.assertTrue(host.structures().stream().filter(p->!p.pier()&&Math.abs(p.a().y()-drive.first().center().y())<.01&&p.height()<2).noneMatch(p->RoadClearance.structureInvades(p,drive,4.25)),"actual DETACH furniture narrows full-width neighboring lane");
 }
 private static void mouthRails(GameTestHelper h,RoadData data,RoadRecord ramp){
  var link=LaneTopology.metadata(ramp).link();var all=LaneTopology.records(data);var pieces=new ArrayList<RoadStructures.Part>(ramp.structures());
  for(boolean first:new boolean[]{true,false}){
   var ref=first?link.from():link.to();var pos=LaneRoadChain.of(all,ref).at(first?0:link.targetOffset());var road=pos.road();pieces.addAll(road.structures());
   var lane=LanePoints.lane(road.rawMesh(),pos.point());var at=RoadStructures.sample(road.rawMesh(),lane.station());
   double side=Math.signum(lane.position().sub(at.center()).dot(at.left()));var edge=at.at(side*at.halfWidth(),0);var end=first?ramp.mesh().first():ramp.mesh().last();
   double rampSide=Math.signum(edge.sub(end.center()).dot(end.left()));
   h.assertTrue(end.at(rampSide*end.halfWidth(),0).distance(edge)<1e-5,"actual ramp shoulder does not meet host");
   var expected=at.at(side*(at.halfWidth()-RoadRailJoin.INSET),0);double distance=Double.POSITIVE_INFINITY;
   for(var p:pieces)if(!p.pier()&&p.material()==RoadStructures.Material.CONCRETE&&Math.abs(p.height()-.45)<1e-7){var delta=p.b().sub(p.a());double u=Math.max(0,Math.min(1,expected.sub(p.a()).dot(delta)/Math.max(1e-10,delta.dot(delta))));distance=Math.min(distance,expected.distance(p.a().add(delta.mul(u))));}
   h.assertTrue(distance<.02,"actual outer rail gap at ramp mouth: "+distance);
  }
 }
 private static void assertSeam(GameTestHelper h,RoadRecord a,RoadRecord b){
  var x=a.caps(0).mesh().last();var y=b.caps(0).mesh().first();
  h.assertTrue(x.center().distance(y.center())<1e-6,"A/B seam center mismatch: "+x.center().distance(y.center()));
  h.assertTrue(Math.abs(x.halfWidth()-y.halfWidth())<1e-6,"A/B seam width mismatch");
  var lx=RoadProfile.layout(a.caps(0).mesh(),x);var ly=RoadProfile.layout(b.caps(0).mesh(),y);
  // Permanent cuts retain authored divider axes for stable IDs; only interior
  // dividers are painted. Compare the rendered set, not hidden metadata entries.
  var dx=lx.dividers().stream().filter(d->d>lx.motorMin()+.12&&d<lx.motorMax()-.12).toList();
  var dy=ly.dividers().stream().filter(d->d>ly.motorMin()+.12&&d<ly.motorMax()-.12).toList();
  h.assertTrue(dx.size()==dy.size(),"A/B seam visible divider count mismatch");
  for(int i=0;i<dx.size();i++)h.assertTrue(Math.abs(dx.get(i)-dy.get(i))<1e-5,"A/B visible divider offset mismatch");
 }
}
