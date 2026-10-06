package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadProfile.*;
import java.util.*;
/** Real planner/records, explicit NBT/world adapters. Not a game-world benchmark. */
public final class Hotfix429ModelValidation {
 static long sequence;static int checks,cases;
 static UUID id(){return new UUID(429,++sequence);}
 static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static void near(double a,double b,String s){check(Math.abs(a-b)<1e-6,s+" "+a+" vs "+b);}
 static RoadRecord road(V a,V b,Settings s){var d=b.sub(a);var n=new Node(a,RoadPlanner.yaw(d.horizontalUnit()),d.y()/d.horizontalLength());return new RoadRecord(id(),new UUID(0,1),RampJunctions.at(a),RampJunctions.at(b),n,new Node(b,n.yaw(),n.grade()),s,true,0);}
 static Settings settings(Type type,int f,int rev,boolean left){return RoadLanes.configure(new Settings(Mode.STRAIGHT,Style.O4_RAIL,18,1,.4,90).options(Options.DEFAULT.traffic(left).route(Routing.DEFAULT.fit(false))),type,new RoadLanes.Counts(f,rev),type==Type.HIGHWAY?5:4);}
 static LanePoints.Ref point(Map<UUID,RoadRecord> all,RoadRecord r,double at,int slot){var p=LanePoints.point(id(),LanePoints.Origin.MANUAL,r.mesh(),at,slot);var ps=new ArrayList<>(LaneTopology.metadata(r).points());ps.add(p);all.put(r.id(),r.withLanePoints(LaneTopology.metadata(r).points(ps)));return LanePoints.Ref.lane(r.id(),p.id());}
 static void addition(Type type,int n,int rev,boolean left,int sign){
  cases++;var target=road(new V(0,100,0),new V(0,100,700),settings(type,n,rev,left));var before=target.mesh();
  double station=sign>0?450:250;var live=LaneSections.live(before,station);int count=live.count(sign);
  var chosen=live.lanes().stream().filter(l->l.sign()==sign).findFirst().orElseThrow();var sample=RoadStructures.sample(before,station);var layout=RoadProfile.layout(before,sample);int side=LaneAdditions.side(layout,sign);
  var tangent=chosen.direction();var location=sample.center().add(sample.left().mul(side*(before.first().halfWidth()+70))).sub(tangent.mul(320));
  var source=road(location,location.add(tangent.mul(180)),settings(type,1,0,left));
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(target.id(),target);all.put(source.id(),source);
  var a=point(all,source,60,0);var b=point(all,target,station,chosen.index());var connection=id();
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.ADD,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  var link=new LanePoints.Link(a,b,options,null);var snapshot=all.get(target.id()).header();long start=System.nanoTime();
  if(count==4){boolean denied=false;try{LaneRamps.generate(null,all,connection,new UUID(0,1),link);}catch(IllegalArgumentException ex){denied=ex.getMessage().contains("4")||ex.getMessage().contains("5");}check(denied,"4->5 not rejected");check(all.get(target.id()).header().equals(snapshot),"rejection mutated source");return;}
  var built=LaneRamps.generate(null,all,connection,new UUID(0,1),link);all.put(built.id(),built);LaneCrossSections.reconcile(all);target=all.get(target.id());var mesh=target.mesh();
  System.out.printf(Locale.ROOT,"ADD429 %s %d+%d left=%s sign=%d planner_ms=%.2f%n",type,n,rev,left,sign,(System.nanoTime()-start)/1e6);
  check(LaneSections.live(mesh,station+sign*60).count(sign)==count+1,"not n+1 downstream");
  check(LaneSections.live(mesh,station-sign*60).count(sign)==count,"upstream lane count changed");
  if(rev>0)check(LaneSections.live(mesh,station+sign*60).count(-sign)==live.count(-sign),"opposite changed");
  for(var old:live.lanes())check(LanePoints.lane(mesh,station+sign*60,old.index()).position().distance(LanePoints.lane(before,station+sign*60,old.index()).position())<1e-6,"old centre shifted");
  near(RoadStructures.sample(mesh,station+sign*60).halfWidth()*2,RoadStructures.sample(before,station+sign*60).halfWidth()*2+chosen.width(),"road did not physically grow");
  var added=LaneAdditions.owned(mesh,connection);var newLane=LanePoints.lane(mesh,station+sign*60,added.slot());check(LaneSections.edge(mesh,newLane.station(),added.slot()),"new outer lane not outer");
  var picked=LanePoints.clicked(mesh,newLane.position());check(picked.index()==added.slot(),"new outer slot cannot be selected");
  var restored=RoadRecord.load(target.header());check(LaneTopology.metadata(restored).additions().equals(LaneTopology.metadata(target).additions()),"addition codec changed");check(restored.mesh().samples().equals(mesh.samples()),"addition mesh changed on reload");
  LaneRamps.validate(built.mesh(),all,built.id(),LaneTopology.metadata(built).link());
  var arrival=LanePoints.lane(mesh,station,added.slot());check(built.mesh().last().center().distance(arrival.position())<1e-5,"connector did not reach new centre");
  var endSection=RoadEndpointSections.section(target.caps(0).mesh(),sign<0,false);check(RoadLanes.counts(endSection).total()==n+rev+1,"endpoint does not see added lane");
  all.remove(connection);LaneCrossSections.reconcile(all);check(all.get(target.id()).header().equals(snapshot),"deleting ADD failed to restore untouched host");
 }
 public static void main(String[] args){
  for(var type:List.of(Type.ORDINARY,Type.HIGHWAY))for(boolean left:new boolean[]{false,true}){
   for(int n=1;n<=4;n++)addition(type,n,0,left,1);
   for(int n=1;n<=4;n++){addition(type,n,2,left,1);addition(type,2,n,left,-1);}
  }
  System.out.println("Hotfix429ModelValidation: "+cases+" cases / "+checks+" checks PASS; actual planner and codec, NOT game-world timing");
 }
}
