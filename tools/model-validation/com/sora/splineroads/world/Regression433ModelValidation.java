package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadProfile.Type;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Physical chain, contact-derived closure and local tunnel regressions.
 * Production records/planner, explicit Minecraft adapters. */
public final class Regression433ModelValidation {
 static int checks;
 static void ok(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static final UUID OWNER=new UUID(433,0), CONNECTION=new UUID(433,999);
 static RoadRecord road(V a,V b,int forward,int reverse){return Hotfix429ModelValidation.road(a,b,Hotfix429ModelValidation.settings(Type.ORDINARY,forward,reverse,false));}
 static LanePoints.Ref point(Map<UUID,RoadRecord> all,RoadRecord r,double d,int slot){return Hotfix429ModelValidation.point(all,r,d,slot);}
 static void chain(){
  var a=road(new V(0,100,0),new V(0,100,100),3,2);var b=road(new V(0,100,100),new V(0,103,300),3,3);
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(a.id(),a);all.put(b.id(),b);
  int slot=LaneSections.live(a.mesh(),95).lanes().stream().filter(l->l.sign()>0).findFirst().orElseThrow().index();
  var ref=point(all,a,95,slot);var chain=LaneRoadChain.of(all,ref);
  ok(chain.ids().size()==2,"unrelated reverse count/grade incorrectly cuts physical chain");
  var end=chain.at(88);ok(end.road().id().equals(b.id()),"88 blocks did not cross seam");ok(Math.abs(end.station()-83)<1e-6,"88 is not along lane arc length");
  ok(LanePoints.lane(end.road().mesh(),end.point()).sign()>0,"mapped to opposing traffic");
  ok(LaneRoadChain.of(all,ref).at(-88).road().id().equals(a.id()),"backward tracking failed");
  var branch=road(new V(0,100,100),new V(100,100,100),3,2);all.put(branch.id(),branch);
  ok(LaneRoadChain.of(all,ref).ids().size()==1,"ambiguous three-way junction was guessed through");
 }
 static void actualCrossSeam(){
  var t1=road(new V(0,100,0),new V(0,100,200),3,2);var t2=road(new V(0,100,200),new V(0,100,500),3,3);
  var source=road(new V(-80,92,0),new V(-80,92,160),2,0);
  var all=new LinkedHashMap<UUID,RoadRecord>();for(var r:List.of(source,t1,t2))all.put(r.id(),r);
  int targetSlot=LaneSections.live(t1.mesh(),195).lanes().stream().filter(l->l.sign()>0).reduce((a,b)->b).orElseThrow().index();
  var a=point(all,source,60,0);var b=point(all,t1,195,targetSlot);
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.FLEXIBLE);
  var link=new LanePoints.Link(a,b,options,null,80).withProtectedMerge().withRectangularClosure();
  var id=new UUID(433,881);var ramp=LaneRamps.generate(null,all,id,OWNER,link);
  ok(LaneTopology.metadata(ramp).link().targetOffset()==80,"legal cross-seam target silently fell back to root");
  var expected=LaneRoadChain.of(all,b).at(80);ok(ramp.mesh().last().center().distance(LanePoints.lane(expected.road().mesh(),expected.point()).position())<1e-5,"cross-seam arrival did not dock on actual next road");
  all.put(id,ramp);LaneCrossSections.reconcile(all);LaneRamps.validate(ramp.mesh(),all,id,LaneTopology.metadata(ramp).link());
  ok(LaneTopology.metadata(all.get(t2.id())).cuts().stream().anyMatch(c->c.connection().equals(id)),"reservation not assigned to actual next host");
  all.remove(id);LaneCrossSections.reconcile(all);ok(all.values().stream().allMatch(r->LaneTopology.metadata(r).cuts().isEmpty()),"cross-seam reservations remain after delete");
 }
 static void footprint(boolean under){
  var host=road(new V(0,100,0),new V(0,100,400),3,0);var all=new LinkedHashMap<UUID,RoadRecord>();all.put(host.id(),host);
  var ref=point(all,host,100,2);var chosen=LanePoints.lane(host.mesh(),100,2);V a=chosen.position(),b=a.add(chosen.direction().mul(80)).add(chosen.direction().left().mul(-40)).add(new V(0,under?-12:12,0));
  var opts=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.FLOW,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  var link=new LanePoints.Link(ref,LanePoints.Ref.lane(host.id(),new UUID(433,998)),opts,null).withProtectedMerge().withRectangularClosure();
  var settings=new Settings(Mode.CURVE,Style.C1_RAMP,4,1,.35,90).options(RoadProfile.Options.DEFAULT.lanePoints(LanePoints.Data.EMPTY.link(link)));
  V left=b.sub(a).horizontalUnit().left();var ramp=RoadRibbon.mesh(List.of(new Sample(a,left,0,2),new Sample(b,left,Math.hypot(80,40),2)),settings);
  var events=new HashMap<UUID,List<LaneSections.Event>>();LaneRoadChain.of(all,ref).reserve(events,CONNECTION,ramp,List.of(),false,0,32,true);
  var cuts=LaneSections.derive(host.rawMesh(),events.get(host.id()));ok(cuts.size()==1,"missing footprint");var cut=cuts.get(0);
  ok(cut.end()-cut.begin()<50,"fixed 96m reservation remains");ok(cut.underpass()==under,"wrong vertical classification");
  var again=new HashMap<UUID,List<LaneSections.Event>>();LaneRoadChain.of(all,ref).reserve(again,CONNECTION,ramp,List.of(),false,0,64,true);
  ok(Math.abs(again.get(host.id()).get(0).returnStation()-cut.end())<1e-7,"rectangular influence tied to fixed transition");
  var built=host.withLanePoints(LanePoints.Data.EMPTY.cuts(cuts));ok(LaneClosureWarnings.paint(built.mesh()).isEmpty(),"valid departure still marked forbidden");
  Ground ground=new Ground(){public double top(double x,double z,double y){return y-1;}public boolean joined(V p){return false;}public boolean blocked(Part p){return false;}};
  if(under)ok(LaneClosureLandscape.plan(built.mesh(),ground).isEmpty(),"underpass received overpass vegetation");
  else ok(!LaneClosureLandscape.plan(built.mesh(),ground).isEmpty(),"above-grade blocked ground lane lost protective bed");
  ok(LaneTopology.metadata(RoadRecord.load(built.header())).cuts().equals(cuts),"underpass footprint not persisted");
 }
 static void geometry(){
  var s=new Settings(Mode.STRAIGHT,Style.C1_RAMP,4,1,.35,90);
  var p=new Part(new V(0,2,10),new V(0,5,20),2,1,false,Material.CONCRETE);
  var m=LaneReopening.envelope(p,s);ok(m.first().center().z()==10&&m.last().center().z()==20,"structure range padded beyond actual endpoints");
  for(int i=0;i<4;i++){var base=p.base().get(i);boolean found=false;for(var at:m.samples())for(int side:new int[]{-1,1})if(at.at(side*at.halfWidth(),p.height()).distance(base)<1e-8)found=true;ok(found,"structure exact base changed");}
  var from=LanePoints.Ref.lane(new UUID(433,1),new UUID(433,2));var to=LanePoints.Ref.lane(new UUID(433,3),new UUID(433,4));
  var link=new LanePoints.Link(from,to,LanePoints.Options.DEFAULT,null);
  s=s.options(s.options().lanePoints(LanePoints.Data.EMPTY.link(link)));
  var mesh=RoadRibbon.mesh(List.of(new Sample(new V(0,10,0),new V(1,0,0),0,2),new Sample(new V(0,10,100),new V(1,0,0),100,2)),s);
  Ground hill=new Ground(){public double top(double x,double z,double y){return z>=30&&z<=70?y:0;}public boolean joined(V v){return false;}public boolean blocked(Part p){return false;}};
  var regions=RoadAutoTunnels.detect(mesh,hill);ok(regions.size()==1&&regions.get(0).from()==30&&regions.get(0).to()==70,"local tunnel is not bounded by actual cover");
  var parts=RoadAutoTunnels.enclose(mesh,hill,List.of());ok(parts.stream().anyMatch(p1->p1.material()==Material.TUNNEL),"no tunnel lining");
  ok(parts.stream().filter(p1->p1.material()==Material.TUNNEL&&p1.height()<.1).allMatch(p1->Math.abs(p1.a().y()-14)<1e-7),"roof actually placed below road");ok(parts.stream().anyMatch(Part::luminous),"no tunnel lights");
  ok(RoadAutoTunnels.regions(parts).equals(regions),"persisted tunnel extents changed");
  var tube=RoadAutoTunnels.tube(mesh,regions.get(0));ok(Math.abs(RoadTunnelSpace.ceiling(tube,0,45)-14)<1e-6,"local tunnel air not aligned with actual roof");
  ok(!Double.isFinite(RoadTunnelSpace.ceiling(tube,0,15)),"tunnel clearance leaked beyond portal");
 }
 public static void main(String[] args){chain();actualCrossSeam();footprint(false);footprint(true);geometry();System.out.println("Regression433ModelValidation: "+checks+" production checks PASS (explicit world adapters)");}
}
