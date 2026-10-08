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
  for(boolean first:new boolean[]{true,false}){
   var host=first?source:target;double station=first?80:150;var lane=LanePoints.lane(host.rawMesh(),station,slot);
   var sample=first?ramp.mesh().first():ramp.mesh().last();var section=RoadProfile.layout(ramp.mesh(),sample);
   check(LaneRampAlignment.axis(ramp.mesh(),first).distance(lane.position())<1e-5,"mouth shoulder shifted motor axis");
   check(Math.abs(section.laneWidth()-lane.width())<1e-5,"mouth motor lane width mismatch");
   var at=RoadStructures.sample(host.rawMesh(),station);double side=Math.signum(lane.position().sub(at.center()).dot(at.left()));
   V expected=at.at(side*at.halfWidth(),0);double localSide=Math.signum(expected.sub(sample.center()).dot(sample.left()));
   check(sample.at(localSide*sample.halfWidth(),0).distance(expected)<1e-5,"outside shoulder/rail rim does not meet host pavement");
  }
  var cut=LaneTopology.metadata(all.get(source.id())).cuts().get(0);
  check(cut.removed(80+.01)>.999,"whole-lane DETACH still tapers inward for "+cut.transition()+"m; removal just after A="+cut.removed(80+.01));
  for(var lane:LaneSections.live(source.mesh(),80).lanes())if(lane.index()!=slot)for(double station:new double[]{79,80.01,96,114}){
   var before=LanePoints.lane(source.mesh(),station,lane.index());var after=LanePoints.lane(all.get(source.id()).mesh(),station,lane.index());
   check(before.position().distance(after.position())<1e-6&&Math.abs(before.width()-after.width())<1e-6,"DETACH changed an adjacent or opposing lane");
  }
  var legacy=LaneSections.derive(source.rawMesh(),List.of(new LaneSections.Event(UUID.randomUUID(),LaneSections.Kind.DEPART,slot,1,80,32))).get(0);
  check(legacy.removed(80+.01)<.01,"genuine manual lane-merge taper disappeared");
 }
 static void joinedFurniture(){
  var settings=new Settings(Mode.CURVE,Style.C1_RAMP,4,1,.4,90);
  var parent=Hotfix429ModelValidation.road(new V(0,20,0),new V(0,20,150),settings);
  var ref=LanePoints.Ref.lane(UUID.randomUUID(),UUID.randomUUID());
  var oldLink=new LanePoints.Link(ref,LanePoints.Ref.lane(UUID.randomUUID(),UUID.randomUUID()),LanePoints.Options.DEFAULT,null);
  parent=parent.withLanePoints(LanePoints.Data.EMPTY.link(oldLink));
  var child=Hotfix429ModelValidation.road(new V(0,20,40),new V(60,20,140),settings);
  child=child.withLanePoints(LanePoints.Data.EMPTY.link(new LanePoints.Link(LanePoints.Ref.lane(parent.id(),UUID.randomUUID()),LanePoints.Ref.lane(UUID.randomUUID(),UUID.randomUUID()),LanePoints.Options.DEFAULT,null)));
  var local=new RoadStructures.Part(new V(1.7,20,40),new V(1.7,20,44),.3,1,false,RoadStructures.Material.STEEL);
  var decorated=parent.structures(List.of(local));
  check(RoadInteractions.invades(local,child.mesh()),"fixture lacks a joining-mouth rail conflict");
  check(RoadInteractions.influences(new RoadIndex.Built(child),new RoadIndex.Built(decorated)),"parent ramp furniture not replanned at branch mouth");
  var at=RoadStructures.sample(child.mesh(),80).center();var remote=new RoadStructures.Part(at,at,.8,2,true,RoadStructures.Material.CONCRETE);
  boolean denied=false;try{RoadInteractions.influences(new RoadIndex.Built(child),new RoadIndex.Built(parent.structures(List.of(remote))));}catch(IllegalArgumentException e){denied=e.getMessage().contains(parent.id().toString());}
  check(denied,"out-of-mouth parent structure collision lost road identity or was exempted");
 }
 static void stableTerrain(){
  var settings=RoadLanes.configure(new Settings(Mode.STRAIGHT,Style.O6_GREEN,24,1,.4,90).options(RoadProfile.Options.DEFAULT.route(RoadProfile.Routing.DEFAULT.fit(false))),RoadProfile.Type.ORDINARY,new RoadLanes.Counts(2,3),4).structure(Structure.AUTO);
  var initial=Hotfix429ModelValidation.road(new V(0,20,0),new V(0,20,80),settings);
  var unstable=new RoadStructures.Ground(){public double top(double x,double z,double y){return x> -5.7?19:0;}public boolean blocked(RoadStructures.Part p){return false;}public boolean joined(V p){return false;}};
  var first=RoadStreetscape.classify(initial.mesh(),unstable);var next=initial.derivedStreetscape(initial.settings().options().streetscape().raisedSpans(first));
  var second=RoadStreetscape.classify(next.mesh(),unstable);
  check(!first.equals(second),"fixture did not reproduce the terrain/median feedback loop");
  var ref=initial.terrainClassificationMesh();
  var stable=new RoadStructures.Ground(){public Mesh terrainReference(Mesh ignored){return ref;}public double top(double x,double z,double y){return unstable.top(x,z,y);}public boolean blocked(RoadStructures.Part p){return false;}public boolean joined(V p){return false;}};
  for(int pass=0;pass<5;pass++){
   var spans=RoadStreetscape.classify(next.mesh(),stable);check(spans.equals(first),"derived median changed terrain classification");
   next=next.derivedStreetscape(next.settings().options().streetscape().raisedSpans(spans));
   check(next.terrainClassificationMesh().samples().equals(ref.samples()),"terrain reference moved after derived classification");
  }
 }
 static void addedMouth(){
  var s=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,2,3,false);
  var target=Hotfix429ModelValidation.road(new V(0,100,0),new V(0,100,700),s);double station=450;
  var lane=LaneSections.live(target.mesh(),station).lanes().stream().filter(l->l.sign()==1).findFirst().orElseThrow();
  var sample=RoadStructures.sample(target.mesh(),station);var layout=RoadProfile.layout(target.mesh(),sample);int side=LaneAdditions.side(layout,1);
  var location=sample.center().add(sample.left().mul(side*(sample.halfWidth()+70))).sub(lane.direction().mul(320));
  var source=Hotfix429ModelValidation.road(location,location.add(lane.direction().mul(180)),Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,1,0,false));
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(source.id(),source);all.put(target.id(),target);
  var a=Hotfix429ModelValidation.point(all,source,60,0);var b=Hotfix429ModelValidation.point(all,target,station,lane.index());var id=UUID.randomUUID();
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.ADD,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  var ramp=LaneRamps.generate(null,all,id,source.owner(),new LanePoints.Link(a,b,options,null));all.put(id,ramp);LaneCrossSections.reconcile(all);
  var host=all.get(target.id()).mesh();var added=LaneAdditions.owned(host,id);var actual=LanePoints.lane(host,station,added.slot());
  check(LaneRampAlignment.axis(ramp.mesh(),false).distance(actual.position())<1e-5,"ADD mouth does not reach the new lane axis");
  var edge=RoadStructures.sample(host,station).at(side*RoadStructures.sample(host,station).halfWidth(),0);var end=ramp.mesh().last();double sign=Math.signum(edge.sub(end.center()).dot(end.left()));
  check(end.at(sign*end.halfWidth(),0).distance(edge)<1e-5,"ADD outside shoulder/rail rim mismatch");
 }
 static void beamReservation(){
  var settings=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,1,0,false).structure(Structure.BRIDGE);
  settings=settings.options(settings.options().infrastructure(settings.options().infrastructure().bridge(RoadInfrastructure.Bridge.OVERPASS)));
  var host=Hotfix429ModelValidation.road(new V(0,20,0),new V(0,20,160),settings);
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(host.id(),host);var ref=Hotfix429ModelValidation.point(all,host,100,0);
  var chain=LaneRoadChain.of(all,ref);chain.sweep().settings().validate();
  var samples=new ArrayList<Sample>();
  for(int d=40;d<=100;d++){
   var lane=LanePoints.lane(host.rawMesh(),d,0);double y=d<=60?14.8:14.8+(d-60)*5.2/40;
   samples.add(new Sample(new V(lane.position().x(),y,lane.position().z()),lane.direction().left(),d-40,2));
  }
  var ramp=RoadRibbon.mesh(samples,new Settings(Mode.CURVE,Style.C1_RAMP,4,1,.35,90));
  var events=new LinkedHashMap<UUID,List<LaneSections.Event>>();chain.reserve(events,UUID.randomUUID(),ramp,List.of(),true,0,32,true);
  var cut=LaneSections.derive(host.rawMesh(),events.get(host.id())).get(0);
  check(cut.begin()<40,"arrival reservation left host girder over ramp: begins at "+cut.begin());
 }
 static void ancestorMarkers(){
  var settings=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,1,0,false);
  var all=new LinkedHashMap<UUID,RoadRecord>();
  var a=Hotfix429ModelValidation.road(new V(0,20,0),new V(0,20,100),settings);
  var b=Hotfix429ModelValidation.road(new V(0,20,300),new V(0,20,500),settings);
  var c=Hotfix429ModelValidation.road(new V(90,20,200),new V(90,20,500),settings);
  var unrelated=Hotfix429ModelValidation.road(new V(180,20,200),new V(180,20,500),settings);
  for(var host:List.of(a,b,c,unrelated))all.put(host.id(),host);
  var from=Hotfix429ModelValidation.point(all,a,50,0);var to=Hotfix429ModelValidation.point(all,b,100,0);
  var rampSettings=new Settings(Mode.CURVE,Style.C1_RAMP,4,1,.35,90);
  var parent=Hotfix429ModelValidation.road(new V(0,20,50),new V(0,20,400),rampSettings).withLanePoints(LanePoints.Data.EMPTY.link(new LanePoints.Link(from,to,LanePoints.Options.DEFAULT,null)));
  all.put(parent.id(),parent);from=Hotfix429ModelValidation.point(all,parent,100,0);to=Hotfix429ModelValidation.point(all,c,100,0);
  var child=Hotfix429ModelValidation.road(new V(0,20,150),new V(90,20,300),rampSettings).withLanePoints(LanePoints.Data.EMPTY.link(new LanePoints.Link(from,to,LanePoints.Options.DEFAULT,null)));
  var endpoints=LaneRamps.contactEndpoints(all,child);
  for(var host:List.of(a,b,c,parent,child))check(endpoints.contains(host.a())&&endpoints.contains(host.b()),"missing ancestor host markers: "+host.id());
  check(!endpoints.contains(unrelated.a())&&!endpoints.contains(unrelated.b()),"unrelated marker protection was disabled");
  check(RoadInteractions.deferredLaneContact(a,parent)&&RoadInteractions.deferredLaneContact(b,parent),"host preflight did not defer its own connector to final topology");
  check(!RoadInteractions.deferredLaneContact(unrelated,parent)&&!RoadInteractions.deferredLaneContact(a,unrelated),"unrelated road clearance was deferred");
  var oldRail=new RoadStructures.Part(new V(0,20,60),new V(0,20,64),.3,1,false,RoadStructures.Material.STEEL);
  check(RoadInteractions.influences(new RoadIndex.Built(a),new RoadIndex.Built(parent.structures(List.of(oldRail)))),"host edit did not enlist dependent ramp furniture");
 }
 static void bridgeEdgeReservation(){
 var config=RoadInfrastructure.Config.DEFAULT.gantry(RoadInfrastructure.Gantry.OFF).bridge(RoadInfrastructure.Bridge.OVERPASS);
 var settings=RoadLanes.configure(new Settings(Mode.STRAIGHT,Style.O4_RAIL,Style.O4_RAIL.defaultWidth(),1,.4,90).structure(Structure.BRIDGE).options(RoadProfile.Options.DEFAULT.infrastructure(config)),RoadProfile.Type.ORDINARY,new RoadLanes.Counts(2,3),4);
 settings=settings.options(settings.options().route(settings.options().routing().fit(false)));
 var source=Hotfix429ModelValidation.road(new V(.5,20.25,.5),new V(.5,20.25,200.5),settings);
 var target=Hotfix429ModelValidation.road(new V(90.5,28.25,180.5),new V(90.5,28.25,400.5),settings);
 var all=new LinkedHashMap<UUID,RoadRecord>();all.put(source.id(),source);all.put(target.id(),target);
 var sm=source.mesh();int slot=LaneSections.live(sm,60).lanes().stream().filter(l->l.sign()==1&&LaneSections.edge(sm,60,l.index())).findFirst().orElseThrow().index();
 var from=Hotfix429ModelValidation.point(all,source,60,slot);var to=Hotfix429ModelValidation.point(all,target,140,slot);
 var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.FLEXIBLE);
 var ramp=LaneRamps.generate(null,all,UUID.randomUUID(),source.owner(),new LanePoints.Link(from,to,options,null));all.put(ramp.id(),ramp);LaneCrossSections.reconcile(all);
 var ground=new RoadStructures.Ground(){public double top(double x,double z,double y){return 9;}public boolean joined(V p){return false;}public boolean blocked(RoadStructures.Part p){return false;}};
 for(var original:List.of(source,target)){
  var host=all.get(original.id());
  var parts=RoadInfrastructure.plan(host.mesh(),ground);int conflict=0;
  for(var p:parts)if(!p.pier()&&RoadClearance.structureInvades(p,ramp.mesh(),4.25)&&!(p.height()<=host.settings().thickness()+1e-7&&RoadClearance.belowSurface(p,ramp.mesh(),.025)))conflict++;
  check(conflict==0,"actual bridge edge or beam remains over connector: "+conflict);
  check(parts.stream().anyMatch(p->!p.pier()&&p.material()==RoadStructures.Material.CONCRETE),"all bridge floor structures disappeared");
 }
 var bridge=ramp.settings().structure(Structure.BRIDGE);bridge=bridge.options(bridge.options().infrastructure(bridge.options().infrastructure().bridge(RoadInfrastructure.Bridge.OVERPASS)));
 var edited=LaneRamps.reconfigure(ramp,bridge,all);all.put(edited.id(),edited);LaneCrossSections.reconcile(all);
 var hosts=List.of(all.get(source.id()).mesh(),all.get(target.id()).mesh());
 var joins=new RoadRailJoin(hosts.stream().map(m->new RoadRailJoin.Neighbor(m,true)).toList());
 var actual=new RoadStructures.Ground(){
  public double top(double x,double z,double y){return 9;}public boolean joined(V p){return false;}
  public List<RoadRailJoin.Span> railSpans(V a,V b,V outside){return joins.exposed(a,b,outside);}
  public boolean blocked(RoadStructures.Part p){return hosts.stream().anyMatch(m->RoadClearance.structureInvades(p,m,4.25)&&!(!p.pier()&&p.material()==RoadStructures.Material.CONCRETE&&p.height()<=1&&RoadClearance.belowSurface(p,m,.025)));}
 };
 var parts=RoadInfrastructure.plan(edited.mesh(),actual);
 check(parts.stream().noneMatch(actual::blocked),"edited bridge left an actual structure in a live host lane");
 check(parts.stream().anyMatch(p->!p.pier()&&Math.abs(p.height()-.9)<1e-6),"edited bridge lost all girders");
 check(LaneRamps.monotone(edited.mesh()),"unnecessary crest introduced during bridge edit");
 }
 public static void main(String[]args){detach();joinedFurniture();stableTerrain();addedMouth();beamReservation();ancestorMarkers();bridgeEdgeReservation();System.out.println("Live441ModelValidation: "+checks+" checks PASS; whole-lane departure, exact shoulder mouths, parent furniture, stable terrain classification, beam reservation and ancestor markers");}
}
