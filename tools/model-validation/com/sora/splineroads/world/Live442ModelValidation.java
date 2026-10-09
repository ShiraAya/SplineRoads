package com.sora.splineroads.world;
import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadGeometry.*;import java.util.*;
public final class Live442ModelValidation {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void deleteContinuation(boolean left,int sign){
  var settings=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,sign>0?2:3,sign>0?3:2,left);
  var raw=Hotfix429ModelValidation.road(new V(0,20,0),new V(0,20,300),settings);
  int slot=LaneSections.live(raw.mesh(),150).lanes().stream().filter(l->l.sign()==sign&&LaneSections.edge(raw.mesh(),150,l.index())).findFirst().orElseThrow().index();
  var removed=Hotfix429ModelValidation.road(new V(100,20,0),new V(100,20,300),new Settings(Mode.STRAIGHT,Style.C1_RAMP,4,1,.35,90));
  var cut=new LaneSections.Cut(removed.id(),slot,sign,150,sign>0?400:-100,32,null,false,false,true);
  var host=raw.withLanePoints(LanePoints.Data.EMPTY.cuts(List.of(cut)));boolean first=sign<0;
  var mesh=host.caps(0).mesh();var end=first?mesh.first():mesh.last();
  var section=RoadEndpointSections.section(mesh,first,first);var nextSettings=RoadEndpointSections.inherit(settings,section);
  V direction=end.left().left().mul(first?1:-1);var anchor=RoadMedianAnchor.position(mesh,first);
  var a=new Node(anchor,RoadPlanner.yaw(direction),0);var b=new Node(anchor.add(direction.mul(180)),a.yaw(),0);
  var child=new RoadRecord(UUID.randomUUID(),host.owner(),first?host.a():host.b(),RampJunctions.at(b.position()),a,b,nextSettings,true,4);
  check(child.caps(0).mesh().first().center().distance(end.center())<1e-6,"fixture continuation not aligned");
  var data=new RoadData();for(var r:List.of(host,child,removed))data.index.put(new RoadIndex.Built(r));
  var batch=new ArrayList<RoadIndex.Built>();var deleted=new HashSet<UUID>(Set.of(removed.id()));LaneTopology.reconcileDeletion(data,batch,deleted);
  var all=new LinkedHashMap<>(LaneTopology.records(data));deleted.forEach(all::remove);batch.forEach(r->all.put(r.record.id(),r.record));
  var restored=all.get(host.id());var moved=all.get(child.id());var port=first?restored.caps(0).mesh().first():restored.caps(0).mesh().last();
  check(moved.caps(0).mesh().first().center().distance(port.center())<1e-6,"delete DETACH leaves continuation center offset");
  check(Math.abs(moved.mesh().first().halfWidth()-port.halfWidth())<1e-6,"delete DETACH lacks 2-to-1 seam taper");
  check(moved.end().equals(child.end()),"delete moved remote continuation endpoint");
  check(LaneSections.live(restored.mesh(),sign>0?270:30).count(sign)==2,"host outer lane not restored");
  check(RoadRecord.load(moved.save()).mesh().samples().equals(moved.mesh().samples()),"repaired continuation not persistent");
 }
 static void departureRoute(){
  var settings=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,2,3,false);
  var source=Hotfix429ModelValidation.road(new V(0,20,0),new V(0,20,300),settings);
  var target=Hotfix429ModelValidation.road(new V(90,28,200),new V(90,28,500),settings);
  int slot=LaneSections.live(source.mesh(),80).lanes().stream().filter(l->l.sign()==1&&LaneSections.edge(source.mesh(),80,l.index())).findFirst().orElseThrow().index();
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(source.id(),source);all.put(target.id(),target);
  var a=Hotfix429ModelValidation.point(all,source,80,slot);var b=Hotfix429ModelValidation.point(all,target,150,slot);
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  var id=UUID.randomUUID();var keep=LaneRamps.generate(null,all,id,source.owner(),new LanePoints.Link(a,b,options,null));
  var detach=new LanePoints.Options(options.path(),LanePoints.Departure.DETACH,options.arrival(),24,32,options.elevation(),options.landing());
  var fresh=LaneRamps.generate(null,all,id,source.owner(),new LanePoints.Link(a,b,detach,null));
  check(fresh.alignment().equals(keep.alignment()),"fresh DETACH/TEMPORARY paths differ");
  all.put(id,keep);LaneCrossSections.reconcile(all);var changed=LaneRamps.generate(null,all,id,source.owner(),new LanePoints.Link(a,b,detach,null));
  check(changed.alignment().equals(keep.alignment()),"departure-only edit reroutes saved ramp");
  check(LaneTopology.metadata(changed).link().options().departure()==LanePoints.Departure.DETACH,"kept old departure policy");
 }
 public static void main(String[]args){for(boolean left:new boolean[]{false,true})for(int sign:new int[]{-1,1})deleteContinuation(left,sign);departureRoute();System.out.println("Live442ModelValidation: "+checks+" checks PASS");}
}
