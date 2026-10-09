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
  seam(host,first,child);
  var marker=LanePoints.point(UUID.randomUUID(),LanePoints.Origin.MANUAL,child.mesh(),12,0);child=child.withLanePoints(LanePoints.Data.EMPTY.points(List.of(marker)));
  var data=new RoadData();for(var r:List.of(host,child,removed))data.index.put(new RoadIndex.Built(r));
  var batch=new ArrayList<RoadIndex.Built>();var deleted=new HashSet<UUID>(Set.of(removed.id()));LaneTopology.reconcileDeletion(data,batch,deleted);
  var all=new LinkedHashMap<>(LaneTopology.records(data));deleted.forEach(all::remove);batch.forEach(r->all.put(r.record.id(),r.record));
  var restored=all.get(host.id());var moved=all.get(child.id());var port=first?restored.caps(0).mesh().first():restored.caps(0).mesh().last();
  check(moved.caps(0).mesh().first().center().distance(port.center())<1e-6,"delete DETACH leaves continuation center offset");
  check(Math.abs(moved.mesh().first().halfWidth()-port.halfWidth())<1e-6,"delete DETACH lacks 2-to-1 seam taper");
  check(moved.end().equals(child.end()),"delete moved remote continuation endpoint");
  seam(restored,first,moved);
  check(LaneTopology.point(moved,marker.id()).position().distance(LanePoints.lane(moved.mesh(),LaneTopology.point(moved,marker.id())).position())<1e-5,"delete left continuation manual point off its final lane");
  check(LaneSections.live(restored.mesh(),sign>0?270:30).count(sign)==2,"host outer lane not restored");
  check(RoadRecord.load(moved.save()).mesh().samples().equals(moved.mesh().samples()),"repaired continuation not persistent");
 }
 static void seam(RoadRecord a,boolean first,RoadRecord b){
  var ma=a.caps(0).mesh();var mb=b.caps(0).mesh();var x=first?ma.first():ma.last();var y=mb.first();
  var lx=RoadProfile.layout(ma,x);var ly=RoadProfile.layout(mb,y);double sign=x.left().dot(y.left());
  var dx=lx.dividers().stream().filter(d->d>lx.motorMin()+.12&&d<lx.motorMax()-.12).sorted().toList();
  var dy=ly.dividers().stream().filter(d->d>ly.motorMin()+.12&&d<ly.motorMax()-.12).map(d->d*sign).sorted().toList();
  check(dx.size()==dy.size(),"actual seam divider count mismatch: "+dx+" / "+dy);
  for(int i=0;i<dx.size();i++)check(Math.abs(dx.get(i)-dy.get(i))<1e-5,"actual seam divider mismatch: "+dx+" / "+dy);
 }
 static void departureRoute(){
  var settings=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,2,3,false).structure(Structure.BRIDGE);
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
  all.put(id,changed);LaneCrossSections.reconcile(all);joinedRails(all,changed,source,80,target,150,slot);
  var narrowSettings=changed.settings().taper(4,4).options(changed.settings().options().ends(RoadTransitions.Ends.NONE));
  var oldSamples=changed.mesh().samples().stream().map(q->new Sample(LanePoints.lane(changed.mesh(),q.distance(),0).position(),q.left(),q.distance(),2)).toList();
  var legacy=new RoadRecord(id,changed.owner(),changed.a(),changed.b(),changed.start(),changed.end(),narrowSettings,false,4).alignment(null,RoadRibbon.mesh(oldSamples,narrowSettings));
  all.put(id,legacy);var repaired=LaneRamps.generate(null,all,id,source.owner(),LaneTopology.metadata(legacy).link());
  check(!repaired.alignment().equals(legacy.alignment()),"legacy narrow mouth was retained unchanged");
  check(repaired.mesh().samples().size()==legacy.mesh().samples().size(),"legacy mouth migration rerouted saved centerline");
  for(int i=0;i<oldSamples.size();i++)check(LanePoints.lane(repaired.mesh(),repaired.mesh().samples().get(i).distance(),0).position().distance(oldSamples.get(i).center())<1e-5,"legacy refit moved motor route");
  all.put(id,repaired);LaneCrossSections.reconcile(all);joinedRails(all,repaired,source,80,target,150,slot);
 }
 static void joinedRails(Map<UUID,RoadRecord> all,RoadRecord ramp,RoadRecord source,double from,RoadRecord target,double to,int slot){
  var parts=new ArrayList<RoadStructures.Part>();
  for(var road:all.values()){
   var others=all.values().stream().filter(r->!r.id().equals(road.id())).map(r->r.mesh()).toList();
   var joins=new RoadRailJoin(others.stream().map(m->new RoadRailJoin.Neighbor(m,true)).toList());
   var mouths=RoadRailJoin.mouths(others);
   var ground=new RoadStructures.Ground(){public double top(double x,double z,double y){return 0;}public boolean joined(V p){return false;}public boolean blocked(RoadStructures.Part p){return false;}
    public List<RoadRailJoin.Span> railSpans(V a,V b,V outside){return joins.exposed(a,b,outside);}
    public List<RoadRailJoin.Span> capRailSpans(V a,V b,V outside){var out=new ArrayList<RoadRailJoin.Span>();for(var span:joins.exposed(a,b,outside))out.addAll(mouths.exposedMouth(span.a(),span.b()));return out;}
   };parts.addAll(RoadStructures.plan(road.mesh(),ground));
  }
  for(boolean first:new boolean[]{true,false}){
   var host=first?source:target;double station=first?from:to;
   var lane=LanePoints.lane(host.rawMesh(),station,slot);var at=RoadStructures.sample(host.rawMesh(),station);
   double side=Math.signum(lane.position().sub(at.center()).dot(at.left()));var expected=at.at(side*(at.halfWidth()-RoadRailJoin.INSET),0);
   double nearest=parts.stream().filter(p->!p.pier()&&p.material()==RoadStructures.Material.CONCRETE&&Math.abs(p.height()-.45)<1e-7).mapToDouble(p->segmentDistance(expected,p.a(),p.b())).min().orElse(100);
   check(nearest<.02,"outside railing does not connect at "+(first?"departure":"arrival")+": gap="+nearest);
  }
 }
 static double segmentDistance(V p,V a,V b){var d=b.sub(a);double u=Math.max(0,Math.min(1,p.sub(a).dot(d)/Math.max(1e-10,d.dot(d))));return p.distance(a.add(d.mul(u)));}
 static void earlierEmptyClosure(){
  var settings=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,3,0,false).structure(Structure.GROUND);
  var host=Hotfix429ModelValidation.road(new V(0,20,0),new V(0,20,200),settings);
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(host.id(),host);var ref=Hotfix429ModelValidation.point(all,host,160,0);var chain=LaneRoadChain.of(all,ref);
  var points=new ArrayList<Sample>();for(double d=80;d<=160;d++){var lane=LanePoints.lane(host.rawMesh(),d,0);points.add(new Sample(lane.position().add(new V(0,-6+(d-80)*6/80,0)),lane.direction().left(),d-80,2));}
  var ramp=RoadRibbon.mesh(points,new Settings(Mode.CURVE,Style.C1_RAMP,4,1,.35,90));
  double unsafe=RoadClearance.contacts(chain.sweep(),ramp).stream().filter(RoadClearance.Contact::blocked).mapToDouble(RoadClearance.Contact::from).min().orElseThrow();
  var events=new HashMap<UUID,List<LaneSections.Event>>();var id=UUID.randomUUID();chain.reserve(events,id,ramp,List.of(),true,0,32,true);
  var cuts=LaneSections.derive(host.rawMesh(),events.get(host.id()));var cut=cuts.get(0);
  check(Math.abs(cut.begin()-(unsafe-9))<1e-5,"physical cutoff did not move 8m before old one-block margin");
  check(cut.underpass(),"low arrival lost underpass classification");
  var closed=host.withLanePoints(LanePoints.Data.EMPTY.cuts(cuts)).mesh();
  var ground=new RoadStructures.Ground(){public double top(double x,double z,double y){return 19.8;}public boolean joined(V p){return false;}public boolean blocked(RoadStructures.Part p){return false;}};
  check(LaneClosureLandscape.plan(closed,ground).isEmpty(),"underpass closed slot retained planter, end kerb or paving pad");
  var warnings=LaneClosureWarnings.paint(closed);check(warnings.size()==6,"upstream X warnings disappeared");
  for(var paint:warnings)for(var point:paint.points())check(RoadQueries.horizontal(closed,point).sample().distance()<cut.begin(),"warning painted inside removed region");
 }
 public static void main(String[]args){for(boolean left:new boolean[]{false,true})for(int sign:new int[]{-1,1})deleteContinuation(left,sign);departureRoute();earlierEmptyClosure();System.out.println("Live442ModelValidation: "+checks+" checks PASS");}
}
