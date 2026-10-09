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
 @GameTest(batch="splineroads_live441",template="empty",templateNamespace="splineroads_live441",timeoutTicks=18000)
 public static void branchFromExistingRamp(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);int cx=214000,cz=cx;
  for(int x=cx-108;x<=cx+18;x++)for(int z=cz;z<=cz+522;z++){var p=new BlockPos(x,188,z);level.getChunkAt(p);level.setBlock(p,Blocks.STONE.defaultBlockState(),2);}
  var settings=RoadLanes.configure(Revision32GameTests.road(Style.O1_ONE,Structure.AUTO),RoadProfile.Type.ORDINARY,new RoadLanes.Counts(1,0),4);
  settings=settings.options(settings.options().route(settings.options().routing().fit(false)));
  var source=data.connect(level,null,Revision32GameTests.marker(h,cx,200,cz,0),Revision32GameTests.marker(h,cx,200,cz+80,0),settings,null);
  var target=data.connect(level,null,Revision32GameTests.marker(h,cx,200,cz+350,0),Revision32GameTests.marker(h,cx,200,cz+520,0),settings,null);
  var third=data.connect(level,null,Revision32GameTests.marker(h,cx-90,200,cz+300,0),Revision32GameTests.marker(h,cx-90,200,cz+520,0),settings,null);
  var a=Build429GameTests.point(data,source,40,0);var b=Build429GameTests.point(data,target,80,0);
  var parentOptions=new LanePoints.Options(LanePoints.Path.DIRECT,LanePoints.Departure.BRANCH,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  var parent=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),source.owner(),new LanePoints.Link(a,b,parentOptions,null));LaneRamps.build(data,level,null,parent);
  parent=data.index.roads.get(parent.id()).record;var oldAlignment=parent.alignment();
  var from=Build429GameTests.point(data,parent,100,0);var to=Build429GameTests.point(data,third,140,0);
  var options=new LanePoints.Options(LanePoints.Path.LEFT,LanePoints.Departure.BRANCH,LanePoints.Arrival.EXTRA,24,32,LanePoints.Elevation.OVER,LanePoints.Landing.FLEXIBLE);
  var child=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),source.owner(),new LanePoints.Link(from,to,options,null));
  double parentSlab=parent.settings().thickness();
  h.assertTrue(data.index.roads.get(parent.id()).record.structures().stream().anyMatch(p->blocksDrive(p,child.mesh(),parentSlab)),"fixture lacks old parent furniture across branch mouth");
  System.out.println("LIVE441 CHILD_PLAN_PASS");LaneRamps.build(data,level,null,child);
  h.assertTrue(data.index.roads.get(parent.id()).record.alignment().equals(oldAlignment),"branch rerouted saved parent");
  var built=data.index.roads.get(child.id()).record;
  // A rail occupying the outer .55 m is the normal C1 boundary assembly.
  // The old full-deck assertion also rejected this now-required common rail.
  var at=built.mesh().first();
  var obstructing=new RoadStructures.Part(at.center(),at.center().add(at.left().left().mul(-1)),.42,.45,false,RoadStructures.Material.CONCRETE);
  h.assertTrue(blocksDrive(obstructing,built.mesh(),parentSlab),"branch test must still reject a concrete barrier in the lane centre");
  for(var part:data.index.roads.get(parent.id()).record.structures())h.assertTrue(!blocksDrive(part,built.mesh(),parentSlab),"saved parent furniture still blocks new branch: "+part);
  data.remove(level,null,child.id());
  h.assertTrue(!data.index.roads.containsKey(child.id()),"branch deletion failed");
  var restored=data.index.roads.get(parent.id()).record;
  h.assertTrue(restored.alignment().equals(oldAlignment),"branch deletion rerouted saved parent");
  h.assertTrue(restored.structures().stream().anyMatch(p->blocksDrive(p,child.mesh(),parentSlab)),"branch deletion did not restore parent mouth furniture");
  System.out.println("LIVE441 REAL_WORLD PASS branch: LEFT/BRANCH/EXTRA/OVER/FLEXIBLE build, parent furniture opening and deletion restoration");h.succeed();
 }
 @GameTest(batch="splineroads_live441",template="empty",templateNamespace="splineroads_live441",timeoutTicks=18000)
 public static void asymmetricBankSwitch(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);int cx=216000,cz=cx;
  for(int x=cx-18;x<=cx+18;x++)for(int z=cz-3;z<=cz+103;z++){
   var p=new BlockPos(x,x>=cx-5?199:188,z);level.getChunkAt(p);level.setBlock(p,Blocks.STONE.defaultBlockState(),2);
  }
  var a=Revision32GameTests.marker(h,cx,200,cz,0);var b=Revision32GameTests.marker(h,cx,200,cz+100,0);
  ((NodeEntity)level.getBlockEntity(a)).offsetX=.25;((NodeEntity)level.getBlockEntity(b)).offsetX=.25;
  var s=RoadLanes.configure(Revision32GameTests.road(Style.O6_GREEN,Structure.BRIDGE),RoadProfile.Type.ORDINARY,new RoadLanes.Counts(2,3),4);s=s.options(s.options().route(s.options().routing().fit(false)));
  var road=data.connect(level,null,a,b,s,null);road=data.connect(level,null,a,b,road.settings().structure(Structure.AUTO),road.id());
  var batch=new ArrayList<RoadIndex.Built>(data.index.roads.values());h.assertTrue(!LaneTopology.needsRefresh(data,batch,List.of(road.id())),"bank switch left lane-point drift");
  System.out.println("LIVE441 REAL_WORLD PASS bank: asymmetric GREEN bridge to AUTO converged");h.succeed();
 }
 private static boolean blocksDrive(RoadStructures.Part part,Mesh mesh,double slab){
  boolean floor=!part.pier()&&part.material()==RoadStructures.Material.CONCRETE&&part.height()<=slab+1e-7&&RoadClearance.belowSurface(part,mesh,.025);
  return !floor&&!perimeterRail(part,mesh)&&RoadClearance.structureInvades(part,mesh,4.25);
 }
 /** Independent check of the complete solid, not the planner's sharedRail predicate.
  * The ordinary rail is inset .34 with a .21 half-width footing. Only that outer
  * strip may be occupied; cross-mouth rails, interior rails and piers still fail. */
 private static boolean perimeterRail(RoadStructures.Part part,Mesh mesh){
  if(part.pier()||part.width()>.420001)return false;
  boolean concrete=part.material()==RoadStructures.Material.CONCRETE&&Math.abs(part.height()-.45)<1e-7;
  boolean steel=part.material()==RoadStructures.Material.STEEL&&Math.abs(part.height()-.12)<1e-7;
  boolean post=part.material()==RoadStructures.Material.DARK_STEEL&&part.width()<=.160001&&part.height()<=.750001;
  if(!concrete&&!steel&&!post)return false;
  var corners=part.base();int count=Math.max(1,(int)Math.ceil(part.a().distance(part.b())/.25));
  int side=0;
  for(int i=0;i<=count;i++)for(int edge=0;edge<2;edge++){
   V p=corners.get(edge).add(corners.get(3-edge).sub(corners.get(edge)).mul(i/(double)count));
   var q=RoadQueries.horizontal(mesh,p);var at=q.sample();int current=q.lateral()<0?-1:1;
   if(side!=0&&side!=current)return false;side=current;
   if(Math.abs(q.lateral())<at.halfWidth()-.55001)return false;
   double y=p.y()-at.at(q.lateral(),0).y();
   if(y<-.025||y+part.height()>1.180001)return false;
  }
  return true;
 }
 private static void scenario(GameTestHelper h,boolean ground,int cx){
  var level=h.getLevel();var data=RoadData.get(level);int cz=cx;
  for(int x=cx-18;x<=cx+108;x++)for(int z=cz;z<=cz+402;z++){
   var p=new BlockPos(x,ground?199:188,z);level.getChunkAt(p);level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);
  }
  var settings=RoadLanes.configure(Revision32GameTests.road(Style.O4_RAIL,ground?Structure.AUTO:Structure.BRIDGE),RoadProfile.Type.ORDINARY,new RoadLanes.Counts(ground?1:2,ground?0:3),4);
  settings=settings.options(settings.options().route(settings.options().routing().fit(false)).infrastructure(settings.options().infrastructure().bridge(RoadInfrastructure.Bridge.OVERPASS)));
  var source=data.connect(level,null,Revision32GameTests.marker(h,cx,200,cz,0),Revision32GameTests.marker(h,cx,200,cz+200,0),settings,null);
  var target=data.connect(level,null,Revision32GameTests.marker(h,cx+90,ground?200:208,cz+180,0),Revision32GameTests.marker(h,cx+90,ground?200:208,cz+400,0),settings,null);
  var sourceMesh=source.mesh();int slot=LaneSections.live(sourceMesh,60).lanes().stream().filter(l->l.sign()==1&&LaneSections.edge(sourceMesh,60,l.index())).findFirst().orElseThrow().index();
  var from=Build429GameTests.point(data,source,60,slot);var to=Build429GameTests.point(data,target,140,slot);
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.FLEXIBLE);
  var ramp=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),source.owner(),new LanePoints.Link(from,to,options,null));
  double surfaceY=source.start().position().y();
  if(ground)h.assertTrue(ramp.mesh().samples().stream().allMatch(s->Math.abs(s.center().y()-surfaceY)<.01),"ground fixture must remain flat and unburied");
  System.out.println("LIVE441 PLAN_PASS ground="+ground);LaneRamps.build(data,level,null,ramp);
  System.out.println("LIVE441 BUILD_PASS ground="+ground);
  if(!ground){
   ramp=data.index.roads.get(ramp.id()).record;var bridge=ramp.settings().structure(Structure.BRIDGE);bridge=bridge.options(bridge.options().infrastructure(bridge.options().infrastructure().bridge(RoadInfrastructure.Bridge.OVERPASS)));
   var edit=LaneRamps.reconfigure(ramp,bridge,LaneTopology.records(data));LaneRamps.build(data,level,null,edit);System.out.println("LIVE441 RAMP_OVERPASS_PASS");
   ramp=data.index.roads.get(ramp.id()).record;edit=LaneRamps.reconfigure(ramp,ramp.settings().structure(Structure.AUTO),LaneTopology.records(data));LaneRamps.build(data,level,null,edit);System.out.println("LIVE441 RAMP_AUTO_PASS");
   source=data.index.roads.get(source.id()).record;
   System.out.println("LIVE441 HOST_SWITCH logical="+data.streets.containsKey(source.id())+" handles="+AutoJunctions.handles(data,source.a(),source.b(),source.id(),false));
   try{data.connect(level,null,source.a(),source.b(),source.settings().structure(Structure.AUTO),source.id());}catch(RuntimeException e){e.printStackTrace();throw e;}
   System.out.println("LIVE441 SWITCH_PASS");}
  else {
   var host=data.index.roads.get(target.id()).record;
   h.assertTrue(host.structures().stream().anyMatch(p->p.material()==RoadStructures.Material.GREEN),"roomy exterior ground arrival lost its planter");
   var rampMesh=data.index.roads.get(ramp.id()).mesh;
   h.assertTrue(host.structures().stream().filter(p->p.material()==RoadStructures.Material.GREEN||p.material()==RoadStructures.Material.SOIL).noneMatch(p->RoadClearance.structureInvades(p,rampMesh,4.25)),"planting enters ramp travel space");
   h.assertTrue(!LaneClosureWarnings.paint(host.mesh()).isEmpty(),"ground closure has no boundary warning");
   h.assertTrue(host.structures().stream().filter(LaneClosureLandscape::paved).allMatch(p->p.a().y()+p.height()<=surfaceY+.025&&p.b().y()+p.height()<=surfaceY+.025),"pavement fill rises above road");
  }
  var planning=new ArrayList<RoadIndex.Built>(data.index.roads.values());var ids=List.of(source.id(),target.id(),ramp.id());
  h.assertTrue(!LaneTopology.needsRefresh(data,planning,ids)&&!LaneCrossSections.needsRestoreRefresh(planning,ids),"saved topology is unstable");
  var saved=RoadData.load(data.save(new net.minecraft.nbt.CompoundTag()));h.assertTrue(saved.index.roads.get(ramp.id()).record.save().equals(data.index.roads.get(ramp.id()).record.save()),"NBT changed joined ramp");
  System.out.println("LIVE441 REAL_WORLD PASS ground="+ground);h.succeed();
 }
}
