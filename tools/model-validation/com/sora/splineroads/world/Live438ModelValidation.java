package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
public final class Live438ModelValidation {
 static int checks;
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void deleteBroken(){
  var settings=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,3,0,false);
  var host=Hotfix429ModelValidation.road(new V(0,100,0),new V(0,100,250),settings);
  var source=Hotfix429ModelValidation.road(new V(-100,100,0),new V(-100,100,250),settings);
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(host.id(),host);all.put(source.id(),source);
  var a=Hotfix429ModelValidation.point(all,source,50,0);var b=Hotfix429ModelValidation.point(all,host,150,1);
  var broken=Hotfix429ModelValidation.road(new V(-80,100,100),new V(80,100,100),new Settings(Mode.STRAIGHT,Style.C1_RAMP,4,1,.35,90));
  broken=broken.withLanePoints(LanePoints.Data.EMPTY.link(new LanePoints.Link(a,b,LanePoints.Options.DEFAULT,null)));
  var removed=Hotfix429ModelValidation.road(new V(-80,108,70),new V(80,108,70),broken.settings());
  host=all.get(host.id());host=host.withLanePoints(LaneTopology.metadata(host).cuts(List.of(new LaneSections.Cut(removed.id(),1,1,50,150,32,null,true,false,true))));
  all.put(host.id(),host);all.put(broken.id(),broken);all.put(removed.id(),removed);
  var data=new RoadData();all.values().forEach(r->data.index.put(new RoadIndex.Built(r)));
  var batch=new ArrayList<RoadIndex.Built>();var deleted=new HashSet<UUID>();deleted.add(removed.id());
  LaneTopology.reconcileDeletion(data,batch,deleted);
  UUID brokenId=broken.id();check(batch.stream().noneMatch(r->r.record.id().equals(brokenId)),"deletion rebuilt a broken surviving ramp");
  var restored=batch.stream().filter(r->r.record.id().equals(hostId(all,a,b))).findFirst().orElseThrow();
  check(LaneTopology.metadata(restored.record).cuts().isEmpty(),"deletion did not release its host reservation");
  check(LaneSections.live(restored.mesh,100).forward()==3,"deletion did not restore host lane count");
 }
 static UUID hostId(Map<UUID,RoadRecord> all,LanePoints.Ref a,LanePoints.Ref b){return b.road();}
 static void recheck(){
  var settings=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,2,0,false);
  var host=Hotfix429ModelValidation.road(new V(0,108,0),new V(0,108,700),settings);
  var source=Hotfix429ModelValidation.road(new V(-160,100,100),new V(-160,100,300),Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,1,0,false));
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(host.id(),host);all.put(source.id(),source);
  var from=Hotfix429ModelValidation.point(all,source,80,0);var to=Hotfix429ModelValidation.point(all,host,480,1);
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.ADD,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.FLEXIBLE);
  var ramp=LaneRamps.generate(null,all,new UUID(438,1),new UUID(0,1),new LanePoints.Link(from,to,options,null));
  var data=new RoadData();all.values().forEach(r->data.index.put(new RoadIndex.Built(r)));Live436ModelValidation.apply(data,ramp,null);
  for(int pass=0;pass<3;pass++){
   var old=data.index.roads.get(ramp.id()).record;all=new LinkedHashMap<>(LaneTopology.records(data));
   var next=LaneRamps.generate(null,all,old.id(),old.owner(),LaneTopology.metadata(old).link());
   check(next.alignment().equals(old.alignment()),"unchanged ADD recheck picked another alignment");
   Live436ModelValidation.apply(data,next,next.id());
   var saved=RoadRecord.load(data.index.roads.get(ramp.id()).record.save());
   check(saved.alignment().equals(old.alignment()),"recheck/codec shifted saved route");
  }
 }
 static void derivedPointRefresh(){
  var options=RoadProfile.Options.DEFAULT;var settings=new Settings(Mode.STRAIGHT,Style.O4_GREEN,RoadProfile.width(Style.O4_GREEN,options,4),1,.35,90).options(options).structure(Structure.AUTO);
  var host=Hotfix429ModelValidation.road(new V(0,100,0),new V(0,100,200),settings);var all=new LinkedHashMap<UUID,RoadRecord>();all.put(host.id(),host);
  var ref=Hotfix429ModelValidation.point(all,host,80,0);host=all.get(host.id());var data=new RoadData();data.index.put(new RoadIndex.Built(host));
  var next=host.derivedStreetscape(options.streetscape().raisedSpans(List.of(new RoadStreetscape.Span(0,200))));
  var batch=new ArrayList<RoadIndex.Built>();batch.add(new RoadIndex.Built(next));
  check(LaneTopology.needsRefresh(data,batch,Set.of(host.id())),"fixture did not reproduce stale raised-profile point");
  LaneTopology.reconcile(data,batch,new HashSet<>(Set.of(host.id())));
  check(!LaneTopology.needsRefresh(data,batch,Set.of(host.id())),"derived-only host was never snapped, causing endless stabilization");
  var saved=batch.get(0).record;check(LaneTopology.point(saved,ref.point()).position().distance(LanePoints.lane(saved.mesh(),LaneTopology.point(saved,ref.point())).position())<1e-6,"point not on final raised lane axis");
 }
 static void explicitTurns(){
  var s=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,1,0,false);
  for(int mirror:new int[]{-1,1}){
   var source=Hotfix429ModelValidation.road(new V(0,100,0),new V(0,100,200),s);
   var target=Hotfix429ModelValidation.road(new V(mirror*200,108,150),new V(mirror*400,108,150),s);
   var all=new LinkedHashMap<UUID,RoadRecord>();all.put(source.id(),source);all.put(target.id(),target);
   var a=Hotfix429ModelValidation.point(all,source,50,0);var b=Hotfix429ModelValidation.point(all,target,100,0);
   var path=mirror<0?LanePoints.Path.RIGHT:LanePoints.Path.LEFT;
   var options=new LanePoints.Options(path,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
   var ramp=LaneRamps.generate(null,all,UUID.randomUUID(),new UUID(0,1),new LanePoints.Link(a,b,options,null));
   check(LaneRamps.matchesTurn(ramp.mesh(),path),"explicit turn used opposite direction or a full loop");
   check(!LaneRamps.matchesTurn(ramp.mesh(),mirror<0?LanePoints.Path.LEFT:LanePoints.Path.RIGHT),"explicit turn filter cannot distinguish left/right");
  }
 }
 public static void main(String[]args){deleteBroken();derivedPointRefresh();recheck();explicitTurns();System.out.println("Live438ModelValidation: "+checks+" checks PASS; deletion recovery, derived points, stable ADD recheck and explicit turns, world adapters");}
}
