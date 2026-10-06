package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_hotfix429") @PrefixGameTestTemplate(false)
public final class Build429GameTests {
 @GameTest(batch="splineroads_hotfix429",template="empty",templateNamespace="splineroads_hotfix429",timeoutTicks=12000)
 public static void ordinaryRoadConstruction(GameTestHelper h){
  System.setProperty("sr.profile","true");var level=h.getLevel();var data=RoadData.get(level);
  for(int x=80000;x<=80320;x++)for(int z=79980;z<=80020;z++){var pos=new BlockPos(x,1,z);level.getChunkAt(pos);level.setBlock(pos,Blocks.GRASS_BLOCK.defaultBlockState(),2);}
  var a=Revision32GameTests.marker(h,80004,2,80000,-90);var b=Revision32GameTests.marker(h,80104,2,80000,-90);var c=Revision32GameTests.marker(h,80304,2,80000,-90);
  var s=Revision32GameTests.road(Style.O6_GREEN,Structure.AUTO);long start=System.nanoTime();var first=data.connect(level,null,a,b,s,null);
  System.out.println("BUILD429 FIRST_REAL_WORLD_MS="+(System.nanoTime()-start)/1e6);h.assertTrue(data.index.roads.containsKey(first.id()),"first committed");
  start=System.nanoTime();var second=data.connect(level,null,b,c,s,null);System.out.println("BUILD429 CONTINUATION_REAL_WORLD_MS="+(System.nanoTime()-start)/1e6);h.assertTrue(data.index.roads.containsKey(second.id()),"continuation committed");h.assertTrue(data.index.roads.get(second.id()).cells.size()>0,"real collision built");h.succeed();
 }
 static LanePoints.Ref point(RoadData data,RoadRecord road,double station,int slot){
  var old=data.index.roads.get(road.id()).record;var p=LanePoints.point(UUID.randomUUID(),LanePoints.Origin.MANUAL,old.mesh(),station,slot);var points=new ArrayList<>(LaneTopology.metadata(old).points());points.add(p);
  var next=old.withLanePoints(LaneTopology.metadata(old).points(points));data.index.put(new RoadIndex.Built(next));data.streets.put(next.id(),next);return LanePoints.Ref.lane(next.id(),p.id());
 }
 @GameTest(batch="splineroads_hotfix429",template="empty",templateNamespace="splineroads_hotfix429",timeoutTicks=12000)
 public static void connectorWithBuiltHostFurniture(GameTestHelper h){
  System.setProperty("sr.profile","true");var level=h.getLevel();var data=RoadData.get(level);
  var ta=Revision32GameTests.marker(h,82000,10,82000,0);var tb=Revision32GameTests.marker(h,82000,10,82600,0);
  var sa=Revision32GameTests.marker(h,81920,2,82040,0);var sb=Revision32GameTests.marker(h,81920,2,82220,0);
  var target=data.connect(level,null,ta,tb,Revision32GameTests.road(Style.O3_ONE,Structure.AUTO),null);
  var source=data.connect(level,null,sa,sb,Revision32GameTests.road(Style.O2_ONE,Structure.AUTO),null);
  var a=point(data,source,60,1);var b=point(data,target,400,2);
  var arrival=LanePoints.Arrival.valueOf(System.getProperty("sr.test429.arrival","MERGE"));
  var opt=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,arrival,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  long start=System.nanoTime();var r=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),source.owner(),new LanePoints.Link(a,b,opt,null));
  System.out.println("BUILD429 RAMP_PLAN_MS="+(System.nanoTime()-start)/1e6);start=System.nanoTime();LaneRamps.build(data,level,null,r);
  System.out.println("BUILD429 RAMP_REAL_WORLD_MS="+(System.nanoTime()-start)/1e6);h.assertTrue(data.index.roads.containsKey(r.id()),"ramp committed");h.assertTrue(data.index.roads.get(r.id()).cells.size()>0,"ramp collision built");
  if(arrival.name().equals("ADD"))h.assertTrue(LaneSections.live(data.index.roads.get(target.id()).mesh,500).forward()==4,"new outer lane persisted");
  h.succeed();
 }
}
