package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Production geometry regression; world/GPU remain separate integration gates. */
public final class Live447ModelValidation {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static Settings sourceSettings(){
  var o=RoadProfile.Options.DEFAULT.lanes(new RoadLanes.Counts(4,4)).cycleFinish(RoadProfile.Options.CycleFinish.ASPHALT);
  o=o.streetscape(o.streetscape().separator(RoadStreetscape.Separator.GREEN)).sidewalk(new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks",true,true));
  return new Settings(Mode.STRAIGHT,Style.O8_GREEN,44,1,.35,90).options(o);
 }
 static void savedEndpoints(){
  for(var departure:List.of(LanePoints.Departure.TEMPORARY,LanePoints.Departure.EXTRA)){
   var source=Hotfix429ModelValidation.road(new V(0,200,300),new V(0,200,0),sourceSettings());
   var target=Hotfix429ModelValidation.road(new V(0,208,-4),new V(0,208,305),new Settings(Mode.STRAIGHT,Style.O6_GREEN,28,1,.35,90));
   var all=new LinkedHashMap<UUID,RoadRecord>();all.put(source.id(),source);all.put(target.id(),target);
   var a=Hotfix429ModelValidation.point(all,source,281.0640078009419,3);var b=Hotfix429ModelValidation.point(all,target,86.8331478396766,5);
   var options=new LanePoints.Options(LanePoints.Path.DIRECT,departure,LanePoints.Arrival.EXTRA,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.FLEXIBLE,false,false);
   long start=System.nanoTime();var r=LaneRamps.generate(null,all,new UUID(447,departure.ordinal()+1),source.owner(),new LanePoints.Link(a,b,options,null));
   var context=LaneCrossSections.staged(all,r.id(),LaneTopology.metadata(r).link(),r.mesh());
   LaneRamps.validate(r.mesh(),context,r.id(),LaneTopology.metadata(r).link());
   LaneRampGrade.validate(r.mesh(),.2);
   check(LaneRampAlignment.axis(r.mesh(),true).distance(LaneRamps.port(all.get(source.id()),LaneTopology.point(all.get(source.id()),a.point())).position())<1e-6,"source moved");
   check(Math.abs(LaneTopology.metadata(r).link().targetOffset())<=88,"landing moved beyond flexible range");
   check(RoadRecord.load(r.header()).mesh().samples().equals(r.mesh().samples()),"saved ramp changes on reload");
   System.out.printf("LIVE447 GEOMETRY %s DIRECT PASS %.3fs%n",departure,(System.nanoTime()-start)/1e9);
  }
 }
 static void edge(){
  var r=Hotfix429ModelValidation.road(new V(0,20,0),new V(0,20,400),new Settings(Mode.STRAIGHT,Style.O6_GREEN,28,1,.35,90));var mesh=r.mesh();
  var at=RoadStructures.sample(mesh,200);var layout=RoadProfile.layout(mesh,at);
  for(int side:new int[]{-1,1}){
   check(Math.abs(LaneDeck.edge(mesh,at,side)-layout.outer(side)-side*.14)<1e-8,"ground shoulder still wide");
   check(Math.abs(RoadSurface.edgeOffset(mesh,at,side)-layout.outer(side))<1e-8,"edge paint changes lane width");
  }
  var rendered=RoadSurface.build(mesh,List.of(),List.of());
  for(var f:rendered.pavement())if(f.texture()==RoadSurface.Texture.PLAIN)for(var p:f.points())if(p.z()>20&&p.z()<380)
   check(Math.abs(p.x())<=Math.abs(layout.outer(1))+.140001,"rendering ignores trimmed collision edge");
  for(var piece:LaneDeck.rasterPieces(mesh,96))for(var q:piece.samples())if(q.distance()>20&&q.distance()<380)
   check(Math.abs(LaneDeck.edge(piece,q,1)-LaneDeck.edge(mesh,q,1))<1e-8,"raster chunk reset shoulder transition");
  for(int sign:new int[]{-1,1}){
   var added=new LaneAdditions.Addition(new UUID(447,20+sign),8,sign,200,32);
   var grown=r.withLanePoints(LanePoints.Data.EMPTY.additions(List.of(added))).mesh();
   for(double delta:new double[]{-24,-16,-8,8,16,24}){
    double station=200+sign*delta;var raw=LaneSections.reference(grown);var sample=RoadStructures.sample(grown,station);
    var lane=LaneAdditions.lane(raw,station,8);int side=LaneAdditions.side(RoadProfile.layout(raw,RoadStructures.sample(raw,station)),sign);
    double seam=lane.position().sub(sample.center()).dot(sample.left())-side*lane.width()*added.fraction(station)/2;
    check(RoadSurface.closedSlotBoundary(grown,station,seam)==(delta<0),"ADD boundary becomes dashed before join or stays solid afterward");
    if(delta<0)for(int edgeSide:new int[]{-1,1})check(Math.abs(LaneDeck.edge(grown,sample,edgeSide)-RoadProfile.layout(grown,sample).outer(edgeSide)-edgeSide*.14)<1e-8,"ADD widened an ordinary shoulder unnecessarily");
   }
  }
 }
 static void lighting(){
  var face=new RoadSurface.Face(List.of(new V(.65,10,.1),new V(1.65,10,.1),new V(1.65,10,.5),new V(.65,10,.5)),0x235994);
  var air=new RoadLighting.Access(){public boolean opaque(int x,int y,int z){return false;}public int packed(int x,int y,int z){return x>=1?12<<20:0;}};
  check(RoadLighting.sample(face,face.points().get(0),air)==12<<20,"thin lit face has a black voxel-edge corner");
  var dark=new RoadLighting.Access(){public boolean opaque(int x,int y,int z){return false;}public int packed(int x,int y,int z){return 0;}};
  check(RoadLighting.sample(face,face.points().get(0),dark)==0,"unlit metal became emissive");
  var wall=new RoadLighting.Access(){public boolean opaque(int x,int y,int z){return x>=1;}public int packed(int x,int y,int z){return x>=1?15<<20:0;}};
  check(RoadLighting.sample(face,face.points().get(0),wall)==0,"opaque centroid leaks light");
 }
 static void oldLighting(){
  var ceiling=new RoadStructures.Part(new V(0,10,0),new V(12,10,0),4,1,false,RoadStructures.Material.TUNNEL);
  var air=new RoadLighting.Access(){public boolean opaque(int x,int y,int z){return y>=10;}public int packed(int x,int y,int z){return y>=10?0:9<<4;}};
  for(var face:ceiling.faces())if(RoadLighting.normal(face).y()<-.9)for(var p:face.points())check(RoadLighting.sample(face,p,air)==9<<4,"ceiling underside now samples opaque roof");
  var down=new RoadSurface.Face(List.of(new V(0,4,0),new V(8,4,8),new V(0,4,8)),0xdddddd);
  var ground=new RoadLighting.Access(){public boolean opaque(int x,int y,int z){return false;}public int packed(int x,int y,int z){return y<4?0:8<<4;}};
  check(RoadLighting.sample(down,down.points().get(0),ground,true)==8<<4,"pavement normal lighting regressed");
 }
 static void cancellation()throws Exception{
  var stop=new AtomicBoolean();var entered=new java.util.concurrent.CountDownLatch(1);var finished=new java.util.concurrent.CountDownLatch(1);var error=new java.util.concurrent.atomic.AtomicReference<Throwable>();
  var worker=new Thread(()->{try(var outer=RoadPlanningBudget.cancellable("preview",stop::get);var nested=RoadPlanningBudget.cancellable("nested geometry")){entered.countDown();while(true)RoadPlanningBudget.check();}catch(RoadPlanningBudget.Aborted expected){}catch(Throwable t){error.set(t);}finally{finished.countDown();}});
  worker.start();check(entered.await(2,java.util.concurrent.TimeUnit.SECONDS),"cancel worker did not start");stop.set(true);
  check(finished.await(2,java.util.concurrent.TimeUnit.SECONDS)&&error.get()==null,"nested preview ignores external cancel token");
 }
 public static void main(String[]args)throws Exception{edge();lighting();oldLighting();cancellation();savedEndpoints();System.out.println("Live447ModelValidation: "+checks+" checks PASS (explicit adapters; not Minecraft/GPU)");}
}
