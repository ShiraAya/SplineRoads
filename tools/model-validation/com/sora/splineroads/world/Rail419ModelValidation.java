package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Actual LaneRamps/LaneTopology/Parts; world and NBT are explicit in-memory adapters. */
public final class Rail419ModelValidation {
 static int cases,checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static RoadRecord configured(RoadRecord r){var s=r.settings();var o=s.options().outerRail(RoadProfile.OuterRail.ON);o=o.infrastructure(o.infrastructure().gantry(RoadInfrastructure.Gantry.OFF));return r.settings(s.options(o).structure(Structure.BRIDGE));}
 static Ground ground(RoadRecord current,Collection<RoadRecord> all,boolean precise){
  var near=all.stream().filter(r->!r.id().equals(current.id())).toList();var index=new RoadRailJoin(near.stream().map(r->new RoadRailJoin.Neighbor(r.mesh(),RoadSurface.higherPriority(r.id(),r.mesh(),current.id(),current.mesh()))).toList());
  return new Ground(){public double top(double x,double z,double y){return y-20;}public boolean blocked(Part p){return false;}public boolean joined(V p){return LanePoints.opening(current.mesh(),p)||near.stream().anyMatch(r->RoadQueries.joins(current.mesh(),r.mesh(),p));}public List<RoadRailJoin.Span> railSpans(V a,V b,V out){return precise?index.exposed(a,b,out):Ground.super.railSpans(a,b,out);}public V railJoint(V p,V d){return precise?index.joint(p,d):null;}public boolean railPost(V p){return !precise||index.ownsPost(p);}public V railJoint(V p,V d,boolean h,boolean r){return precise?index.joint(p,d,h,r):null;}public boolean railPost(V p,boolean h,boolean r){return !precise||index.ownsPost(p,h,r);}};
 }
 public static void main(String[] args){
  for(var style:List.of(Style.O3_ONE,Style.O6_RAIL,Style.H6_RAIL))for(boolean left:new boolean[]{false,true})for(boolean sourceExtra:new boolean[]{false,true}){
   var all=new LinkedHashMap<UUID,RoadRecord>();var a=configured(Arrival417ModelValidation.road(new V(0,100,0),new V(0,100,900),style,left));int slot=!RoadProfile.catalog(style).twoWay()&&left?0:RoadProfile.catalog(style).lanes()-1,sign=LanePoints.lane(a.rawMesh(),500,slot).sign();double za=sign>0?200:700;
   // Keep rail-union fixtures geometrically feasible under actual nonselected-lane protection.
   // Internal-slot EXTRA has its own rejection tests; a crossing needs room before its locked taper.
   var b=configured(Arrival417ModelValidation.road(new V(left&&!RoadProfile.catalog(style).twoWay()?140:-140,100,za+sign*750),new V(left&&!RoadProfile.catalog(style).twoWay()?140:-140,100,za+sign*1500),style,left));all.put(a.id(),a);all.put(b.id(),b);
   var from=Arrival417ModelValidation.point(all,a,za,slot);var to=Arrival417ModelValidation.point(all,b,250,slot);
   var options=new LanePoints.Options(LanePoints.Path.AUTO,sourceExtra?LanePoints.Departure.EXTRA:LanePoints.Departure.BRANCH,sourceExtra?LanePoints.Arrival.MERGE:LanePoints.Arrival.EXTRA,32,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
   var id=Arrival417ModelValidation.id();var ramp=LaneRamps.generate(null,all,id,id,new LanePoints.Link(from,to,options,null));
   var data=new RoadData();all.values().forEach(r->data.index.put(new RoadIndex.Built(r)));var batch=new ArrayList<RoadIndex.Built>(List.of(new RoadIndex.Built(ramp)));var removed=new HashSet<UUID>();LaneTopology.reconcile(data,batch,removed);removed.forEach(data.index.roads::remove);batch.forEach(data.index::put);var updated=LaneTopology.records(data);cases++;
   check(updated.values().stream().anyMatch(r->!LaneTopology.metadata(r).openings().isEmpty()),"actual topology omitted furniture-opening metadata");
   for(var r:updated.values()){
    var parts=RoadStructures.plan(r.mesh(),ground(r,updated.values(),true));
    long rails=parts.stream().filter(p->p.material()==Material.STEEL||p.material()==Material.CONCRETE).count();check(rails>0,"joined road/ramp has no remaining real rails");
    var neighbors=updated.values().stream().filter(n->!n.id().equals(r.id())).map(n->new RoadRailJoin.Neighbor(n.mesh(),RoadSurface.higherPriority(n.id(),n.mesh(),r.id(),r.mesh()))).toList();var index=new RoadRailJoin(neighbors);
    // Every sampled exterior axis admitted by the exact union has rail material,
    // while coincident discarded runs cannot produce a second lateral rail owner.
    var msh=r.mesh();for(double d=3;d<msh.length()-3;d+=13){var q=RoadStructures.sample(msh,d);for(int side:new int[]{-1,1}){
      V p=q.at(side*(q.halfWidth()-.16),0);var q2=RoadStructures.sample(msh,Math.min(msh.length(),d+.03));var span=index.exposed(p,q2.at(side*(q2.halfWidth()-.16),0),p.add(q2.at(side*(q2.halfWidth()-.16),0)).mul(.5).add(q.left().mul(side*.4)));
      if(span.stream().noneMatch(s->s.a().distance(p)<.001&&s.a().distance(s.b())>.005))continue;boolean found=parts.stream().anyMatch(part->{if(part.material()!=Material.STEEL&&part.material()!=Material.CONCRETE)return false;V dir=part.b().sub(part.a());double length=dir.x()*dir.x()+dir.z()*dir.z();if(length<.02)return false;double t=((p.x()-part.a().x())*dir.x()+(p.z()-part.a().z())*dir.z())/length;if(t<-.01||t>1.01)return false;V at=part.a().add(dir.mul(Math.max(0,Math.min(1,t))));return at.sub(p).horizontalLength()<.015&&Math.abs(at.y()-p.y())<1.2;});check(found,"exposed joined contour has a missing longitudinal rail "+style+" left="+left+" extra="+sourceExtra+" owner="+r.id()+" ramp="+id+" station="+d+" side="+side+" p="+p+" spans="+span);
    }}
    var loaded=RoadRecord.load(r.structures(parts).save());check(loaded.structures().equals(parts),"packed rail parts/miter frames do not roundtrip");
   }
   var deleted=new HashSet<>(Set.of(id));var clean=new ArrayList<RoadIndex.Built>();LaneTopology.reconcile(data,clean,deleted);check(clean.stream().filter(r->!r.record.id().equals(id)).allMatch(r->LaneTopology.metadata(r.record).openings().stream().noneMatch(o->o.connection().equals(id))),"delete left stale rail-opening ownership");
   System.out.println("  planned "+style+" left="+left+" sourceExtra="+sourceExtra);
  }
  System.out.println("Rail419ModelValidation: "+cases+" real planning/reconcile/packed-parts/delete cases, "+checks+" checks; fake Ground and NBT/index, NO world/GameTest/client.");
 }
}
