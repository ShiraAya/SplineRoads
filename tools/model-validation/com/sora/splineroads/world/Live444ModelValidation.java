package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
public final class Live444ModelValidation {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static Ground ground(RoadRecord road,List<RoadRecord> all){
  var others=all.stream().filter(r->!r.id().equals(road.id())).toList();
  var join=new RoadRailJoin(road.mesh(),others.stream().map(r->new RoadRailJoin.Neighbor(r.mesh(),RoadSurface.higherPriority(r.id(),r.mesh(),road.id(),road.mesh()))).toList());
  return new Ground(){public double top(double x,double z,double y){return 0;}public boolean joined(V p){return false;}
   public boolean unionRails(){return true;}
   public boolean blocked(Part p){return others.stream().anyMatch(r->RoadClearance.structureInvades(p,r.mesh(),4.25));}
   public boolean railBlocked(Part p,V a,V b){return others.stream().anyMatch(r->!RoadRailJoin.sharedRail(r.mesh(),a,b)&&RoadClearance.structureInvades(p,r.mesh(),4.25));}
   public List<RoadRailJoin.Span> railSpans(V a,V b,V outside,double inset){return join.exposed(a,b,outside,inset);}
   public V railJoint(V p,V d,boolean highway,boolean raised){return join.joint(p,d,highway,raised);}
   public boolean railPost(V p,boolean highway,boolean raised){return join.ownsPost(p,highway,raised);}
  };
 }
 static void fixture(boolean extra,int mirror){
  var style=extra?Style.O3_ONE:Style.C1_RAMP;
  var opts=RoadProfile.Options.DEFAULT.traffic(extra&&mirror>0).outerRail(RoadProfile.OuterRail.ON).hideArrows(true);
  var s=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,opts,4),1,.35,90).structure(Structure.BRIDGE).options(opts);
  var source=Hotfix429ModelValidation.road(new V(1400,20,-700),new V(1400,20,extra?200:-440),s);
  var target=Hotfix429ModelValidation.road(new V(1400+(extra?140:80)*mirror,20,extra?250:-520),new V(1400+(extra?140:80)*mirror,20,extra?1000:-160),s);
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(source.id(),source);all.put(target.id(),target);
  int slot=extra?(mirror==1?0:2):0;
  var a=Hotfix429ModelValidation.point(all,source,extra?200:60,slot);var b=Hotfix429ModelValidation.point(all,target,extra?250:220,slot);
  var options=new LanePoints.Options(LanePoints.Path.AUTO,extra&&mirror>0?LanePoints.Departure.EXTRA:LanePoints.Departure.BRANCH,extra?(mirror>0?LanePoints.Arrival.MERGE:LanePoints.Arrival.EXTRA):LanePoints.Arrival.FLOW,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  var ramp=LaneRamps.generate(null,all,new UUID(444,mirror+2+(extra?10:0)),source.owner(),new LanePoints.Link(a,b,options,null));all.put(ramp.id(),ramp);LaneCrossSections.reconcile(all);
  var records=List.copyOf(all.values());var parts=new ArrayList<Part>();for(var r:records)parts.addAll(RoadStructures.plan(r.mesh(),ground(r,records)));
  if(extra){
   var host=all.get(mirror>0?source.id():target.id());var mesh=host.mesh();boolean departing=mirror>0;double anchor=departing?200:250;int seamChecks=0;
   for(double step=2;step<62;step+=.5){
    double station=anchor+(departing?step:-step);var at=RoadStructures.sample(mesh,station);int side=RoadProfile.layout(mesh,at).outside();
    V edge=at.at(side*(at.halfWidth()+.005),0);var q=RoadQueries.horizontal(ramp.mesh(),edge);
    if(!RoadQueries.contains(ramp.mesh(),edge,0,.025)||side*q.sample().center().sub(at.center()).dot(at.left())<=at.halfWidth())continue;
    V axis=at.at(side*(at.halfWidth()-RoadRailJoin.inset(mesh,at,side)),0);
    check(parts.stream().noneMatch(t->t.material()==Material.CONCRETE&&Math.abs(t.height()-.45)<1e-8&&JunctionPaint.inside(t.base(),axis)),"rail remains inside auxiliary taper seam at="+axis);seamChecks++;
   }
   check(seamChecks>20,"auxiliary seam fixture has no substantial shared edge");
  }
  int missing=0,visible=0;
  for(var r:records){var m=r.mesh();var g=ground(r,records);
   for(double d=2;d<m.length()-2;d+=1)for(int side:new int[]{-1,1}){
    if(LaneDeck.outerOpening(m,d,side))continue;
    var at=RoadStructures.sample(m,d);double inset=RoadRailJoin.inset(m,at,side);V p=at.at(side*(at.halfWidth()-inset),0),delta=at.left().left().mul(-.02);
    if(g.railSpans(p.sub(delta),p.add(delta),p.add(at.left().mul(side)),inset).stream().noneMatch(span->span.a().sub(p).dot(delta)<=1e-9&&span.b().sub(p).dot(delta)>=-1e-9))continue;
    visible++;
    if(parts.stream().noneMatch(t->t.material()==Material.CONCRETE&&Math.abs(t.height()-.45)<1e-8&&JunctionPaint.inside(t.base(),p))){
     missing++;if(missing<5)System.out.println("MISSING extra="+extra+" mirror="+mirror+" ramp="+r.id().equals(ramp.id())+" d="+d+" side="+side+" p="+p);
    }
   }
  }
  check(missing==0,"exposed perimeter has "+missing+" unguarded samples of "+visible+" extra="+extra+" mirror="+mirror);
  check(all.get(source.id()).mesh().samples().equals(source.mesh().samples()),"straight host indents around connector");
 }
 static void legacyKink(){
  var settings=new Settings(Mode.STRAIGHT,Style.C1_RAMP,4,1,.35,90).structure(Structure.BRIDGE);
  var source=Hotfix429ModelValidation.road(new V(0,20,0),new V(0,20,260),settings);
  var target=Hotfix429ModelValidation.road(new V(80,28,180),new V(80,28,540),settings);
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(source.id(),source);all.put(target.id(),target);
  var a=Hotfix429ModelValidation.point(all,source,60,0);var b=Hotfix429ModelValidation.point(all,target,220,0);
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.FLOW,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  var fresh=LaneRamps.generate(null,all,new UUID(444,100),source.owner(),new LanePoints.Link(a,b,options,null));var mesh=fresh.mesh();
  double start=mesh.samples().get(LaneRampThroat.end(mesh,List.of(source.mesh()),true)).distance()+10;
  double end=mesh.samples().get(LaneRampThroat.end(mesh,List.of(target.mesh()),false)).distance()-10;
  var samples=mesh.samples().stream().map(p->new Sample(new V(p.center().x(),20+8*Math.max(0,Math.min(1,(p.distance()-start)/(end-start))),p.center().z()),p.left(),p.distance(),p.halfWidth())).toList();
  var old=fresh.alignment(null,RoadRibbon.mesh(samples,mesh.settings()));all.put(old.id(),old);LaneCrossSections.reconcile(all);
  check(LaneRamps.monotone(old.mesh())&&LaneRamps.verticalKink(old.mesh()),"legacy fixture has no monotone grade corner");
  LaneRamps.validate(old.mesh(),all,old.id(),LaneTopology.metadata(old).link());
  var repaired=LaneRamps.generate(null,all,old.id(),old.owner(),LaneTopology.metadata(old).link());
  check(!LaneRamps.verticalKink(repaired.mesh()),"unchanged preview retained legacy grade kink");
  all.put(repaired.id(),repaired);LaneCrossSections.reconcile(all);
  check(LaneRamps.generate(null,all,repaired.id(),repaired.owner(),LaneTopology.metadata(repaired).link()).alignment().equals(repaired.alignment()),"smooth saved preview drifted on recheck");
 }
 public static void main(String[] args){for(boolean extra:new boolean[]{false,true})for(int mirror:new int[]{1,-1})fixture(extra,mirror);legacyKink();System.out.println("Live444ModelValidation "+checks+" checks PASS");}
}
