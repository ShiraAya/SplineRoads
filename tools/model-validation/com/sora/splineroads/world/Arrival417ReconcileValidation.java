package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
/** Production reconcile before/after index changes, not actual world writes. */
public final class Arrival417ReconcileValidation {
 static int checks;
 static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 public static void main(String[]args){
  for(boolean left:new boolean[]{false,true})for(int slot:new int[]{0,1,4}){
   var all=new LinkedHashMap<UUID,RoadRecord>();var target=Arrival417ModelValidation.road(new V(0,100,0),new V(0,100,1400),Style.O6_RAIL,left);int sign=LanePoints.lane(target.rawMesh(),700,slot).sign();var source=Arrival417ModelValidation.road(new V(-360,100,700-sign*350),new V(-280,100,700-sign*350),Style.O1_ONE,left);all.put(source.id(),source);all.put(target.id(),target);
   var from=Arrival417ModelValidation.point(all,source,60,0);var to=Arrival417ModelValidation.point(all,target,700,slot);var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.MERGE,32,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);var rid=Arrival417ModelValidation.id();var ramp=LaneRamps.generate(null,all,rid,Arrival417ModelValidation.id(),new LanePoints.Link(from,to,options,null));
   var data=new RoadData();all.values().forEach(r->data.index.put(new RoadIndex.Built(r)));var batch=new ArrayList<RoadIndex.Built>(List.of(new RoadIndex.Built(ramp)));var removed=new HashSet<UUID>();LaneTopology.reconcile(data,batch,removed);
   var host=batch.stream().filter(b->b.record.id().equals(target.id())).findFirst().orElseThrow().record;check(LaneTopology.metadata(host).cuts().stream().anyMatch(LaneSections.Cut::arrival),"actual reconcile did not enlist/close target host");
   check(LaneTopology.metadata(data.index.roads.get(target.id()).record).cuts().isEmpty(),"planning mutated saved original");
   removed.forEach(data.index.roads::remove);batch.forEach(data.index::put);var current=LaneTopology.records(data);var saved=data.index.roads.get(rid).record;
   var l=LaneTopology.metadata(saved).link();var legacy=saved.withLanePoints(LaneTopology.metadata(saved).link(new LanePoints.Link(l.from(),l.to(),l.options(),l.junctionMouth(),l.targetOffset())));current.put(rid,legacy);
   var migrated=LaneRamps.reconfigure(legacy,legacy.settings(),current);check(LaneTopology.metadata(migrated).link().protectedMerge(),"explicit old-link edit did not acquire new policy");
   var profile=RoadProfile.layout(migrated.settings(),migrated.settings().width());check(Math.abs(profile.motorCenter())<1e-8&&profile.shoulderWidth()==0,"edited old-link throat uses asymmetric legacy profile");
   batch.clear();removed=new HashSet<>(Set.of(rid));LaneTopology.reconcile(data,batch,removed);host=batch.stream().filter(b->b.record.id().equals(target.id())).findFirst().orElseThrow().record;check(LaneTopology.metadata(host).cuts().isEmpty(),"actual deletion reconcile leaves target closure");
  }
  System.out.println("Arrival417ReconcileValidation: 6 production planning/edit/delete cases, "+checks+" checks. In-memory index/NBT adapters, NO world commit.");
 }
}
