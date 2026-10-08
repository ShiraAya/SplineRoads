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
 public static void main(String[]args){deleteBroken();recheck();System.out.println("Live438ModelValidation: "+checks+" checks PASS; deletion recovery and stable ADD recheck, world adapters");}
}
