package com.sora.splineroads.world;
import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadGeometry.*;import java.util.*;
public final class Live441ModelValidation {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void detach(){
  var s=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,2,3,false).structure(Structure.BRIDGE);
  var source=Hotfix429ModelValidation.road(new V(0,20,0),new V(0,20,300),s);
  var target=Hotfix429ModelValidation.road(new V(90,28,200),new V(90,28,500),s);
  int slot=LaneSections.live(source.mesh(),80).lanes().stream().filter(l->l.sign()==1&&LaneSections.edge(source.mesh(),80,l.index())).findFirst().orElseThrow().index();
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(source.id(),source);all.put(target.id(),target);
  var a=Hotfix429ModelValidation.point(all,source,80,slot);var b=Hotfix429ModelValidation.point(all,target,150,slot);
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.DETACH,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  var ramp=LaneRamps.generate(null,all,UUID.randomUUID(),source.owner(),new LanePoints.Link(a,b,options,null));all.put(ramp.id(),ramp);LaneCrossSections.reconcile(all);
  var cut=LaneTopology.metadata(all.get(source.id())).cuts().get(0);
  check(cut.removed(80+.01)>.999,"whole-lane DETACH still tapers inward for "+cut.transition()+"m; removal just after A="+cut.removed(80+.01));
  for(var lane:LaneSections.live(source.mesh(),80).lanes())if(lane.index()!=slot)for(double station:new double[]{79,80.01,96,114}){
   var before=LanePoints.lane(source.mesh(),station,lane.index());var after=LanePoints.lane(all.get(source.id()).mesh(),station,lane.index());
   check(before.position().distance(after.position())<1e-6&&Math.abs(before.width()-after.width())<1e-6,"DETACH changed an adjacent or opposing lane");
  }
  var legacy=LaneSections.derive(source.rawMesh(),List.of(new LaneSections.Event(UUID.randomUUID(),LaneSections.Kind.DEPART,slot,1,80,32))).get(0);
  check(legacy.removed(80+.01)<.01,"genuine manual lane-merge taper disappeared");
 }
 public static void main(String[]args){detach();System.out.println("Live441ModelValidation: "+checks+" checks PASS; whole-lane departure and preserved adjacent lanes/manual merge");}
}
