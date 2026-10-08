package com.sora.splineroads.world;
import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadGeometry.*;import java.util.*;
public final class Live440ModelValidation {
 static int checks;static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static void publication(){
  var s=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,1,0,false);
  var source=Hotfix429ModelValidation.road(new V(0,100,0),new V(0,100,80),s);var target=Hotfix429ModelValidation.road(new V(90,108,200),new V(90,108,360),s);
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(source.id(),source);all.put(target.id(),target);
  var a=Hotfix429ModelValidation.point(all,source,40,0);var b=Hotfix429ModelValidation.point(all,target,60,0);
  var opt=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.FLEXIBLE);
  var request=new LanePoints.Link(a,b,opt,null);var id=UUID.randomUUID();var road=LaneRamps.generate(null,all,id,source.owner(),request);
  boolean reproduced=false;try{LaneCrossSections.staged(all,id,request,road.mesh());}catch(IllegalArgumentException e){reproduced=e.getMessage().contains("安全恢复");}
  check(reproduced,"fixture did not reproduce 0.40.26 raw-request publication error");
  var resolved=LaneTopology.metadata(road).link();var context=LaneCrossSections.staged(all,id,resolved,road.mesh());LaneRamps.validate(road.mesh(),context,id,resolved);
  var cuts=LaneTopology.metadata(context.get(source.id())).cuts();check(!cuts.isEmpty()&&cuts.get(0).rectangular(),"lost computed rectangle");check(cuts.get(0).end()-cuts.get(0).begin()<64,"fixture requires a short legal closure");
  all.put(id,road);LaneCrossSections.reconcile(all);
  var tunnel=LaneRamps.reconfigure(road,road.settings().structure(Structure.TUNNEL),all);
  check(tunnel.settings().structure()==Structure.TUNNEL,"connector tunnel skin rejected");
 }
 static void mixedBelowAbove(){
  var s=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,1,0,false);var host=Hotfix429ModelValidation.road(new V(0,100,0),new V(0,100,220),s);
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(host.id(),host);var ref=Hotfix429ModelValidation.point(all,host,20,0);
  var samples=new ArrayList<Sample>();for(double d=20;d<=200;d+=.5){var lane=LanePoints.lane(host.mesh(),d,0);double dy=d<80?-2*Math.sin(Math.PI*(d-20)/60):d<160?8*Math.sin(Math.PI*(d-80)/160):8;
   samples.add(new Sample(lane.position().add(new V(0,dy,0)),lane.direction().left(),d-20,2));}
  var ramp=RoadRibbon.mesh(samples,new Settings(Mode.CURVE,Style.C1_RAMP,4,1,.35,90));var events=new HashMap<UUID,List<LaneSections.Event>>();
  LaneRoadChain.of(all,ref).reserve(events,UUID.randomUUID(),ramp,List.of(),false,0,32,true);
  check(events.get(host.id()).get(0).underpass(),"later higher crest re-enabled vegetation above earlier buried stretch");
 }
 public static void main(String[]args){publication();mixedBelowAbove();System.out.println("Live440ModelValidation: "+checks+" checks PASS; raw/normalized link regression, explicit tunnel edit and mixed below/above reservation");}
}
