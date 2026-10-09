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
  check(tunnel.settings().structure()==Structure.AUTO,"connector type was not made local (0446)");
  // Keep the old explicit shell as a geometry-only fixture for portal clipping.
  tunnel=tunnel.settings(tunnel.settings().structure(Structure.TUNNEL));
  var ground=new RoadStructures.Ground(){public double top(double x,double z,double y){return 98;}public boolean blocked(RoadStructures.Part p){return false;}public boolean joined(V p){return false;}};
  var shell=RoadStructures.plan(tunnel.mesh(),ground);
  var center=RoadStructures.sample(tunnel.mesh(),tunnel.mesh().length()/2).center();
  var crossing=Hotfix429ModelValidation.road(center.add(new V(-30,0,0)),center.add(new V(30,0,0)),s);
  var hosts=new ArrayList<RoadIndex.Built>();for(var r:all.values())if(!r.id().equals(tunnel.id()))hosts.add(new RoadIndex.Built(r));hosts.add(new RoadIndex.Built(crossing));
  var hostIds=Set.of(source.id(),target.id());
  check(shell.stream().anyMatch(p->p.material()==RoadStructures.Material.TUNNEL&&hosts.stream().anyMatch(h->hostIds.contains(h.record.id())&&RoadInteractions.invades(p,h.mesh))),"fixture lacks the real lane-mouth shell conflict");
  var opened=RoadInteractions.openPortal(new RoadIndex.Built(tunnel),shell,hosts);
  check(opened.stream().noneMatch(p->p.material()==RoadStructures.Material.TUNNEL&&hosts.stream().anyMatch(h->hostIds.contains(h.record.id())&&RoadInteractions.invades(p,h.mesh))),"lane mouth remains blocked by tunnel shell");
  var blocked=shell.stream().filter(p->p.material()==RoadStructures.Material.TUNNEL&&RoadInteractions.invades(p,crossing.mesh())).toList();
  check(!blocked.isEmpty()&&opened.containsAll(blocked),"unrelated crossing incorrectly excused by portal opening");
  var automatic=tunnel.settings(tunnel.settings().structure(Structure.AUTO));
  var buried=new RoadStructures.Ground(){public double top(double x,double z,double y){return 150;}public boolean blocked(RoadStructures.Part p){return false;}public boolean joined(V p){return false;}};
  var localShell=RoadAutoTunnels.enclose(automatic.mesh(),buried,List.of());
  var localOpen=RoadInteractions.openPortal(new RoadIndex.Built(automatic),localShell,hosts);
  check(localOpen.stream().noneMatch(p->p.material()==RoadStructures.Material.TUNNEL&&hosts.stream().anyMatch(h->hostIds.contains(h.record.id())&&RoadInteractions.invades(p,h.mesh))),"local underground mouth remains blocked");
  var localBlocked=localShell.stream().filter(p->p.material()==RoadStructures.Material.TUNNEL&&RoadInteractions.invades(p,crossing.mesh())).toList();
  check(!localBlocked.isEmpty()&&localOpen.containsAll(localBlocked),"local tunnel mouth exempted unrelated crossing");
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
 public static void main(String[]args){publication();mixedBelowAbove();System.out.println("Live440ModelValidation: "+checks+" checks PASS; raw/normalized link, tunnel mouth opening and unrelated collision preservation, mixed below/above reservation");}
}
