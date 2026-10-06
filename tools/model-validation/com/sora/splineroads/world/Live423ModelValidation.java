package com.sora.splineroads.world;
import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadGeometry.*;import java.util.*;
/** Real planner/topology/material tests against screenshot-shaped layouts; explicit NBT/index adapters. */
public final class Live423ModelValidation {
 static int checks,cases;static long seq=980000;static UUID id(){return new UUID(423,++seq);}
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static RoadRecord road(V a,V b,Style st,boolean left){return Arrival417ModelValidation.road(a,b,st,left);}
 static LanePoints.Ref pt(Map<UUID,RoadRecord>a,RoadRecord r,double d,int slot){return Arrival417ModelValidation.point(a,r,d,slot);}
 static double variation(Mesh m){double v=0;for(int i=1;i<m.samples().size();i++)v+=Math.abs(m.samples().get(i).center().y()-m.samples().get(i-1).center().y());return v;}
 static LanePoints.Options options(LanePoints.Departure d,LanePoints.Arrival a){return new LanePoints.Options(LanePoints.Path.AUTO,d,a,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);}
 static void nearbyElevated(){
  for(boolean left:new boolean[]{false,true})for(int side:new int[]{-1,1})for(int slot:new int[]{2,5})for(var arrival:List.of(LanePoints.Arrival.MERGE,LanePoints.Arrival.EXTRA,LanePoints.Arrival.FLOW)){
   var all=new LinkedHashMap<UUID,RoadRecord>();var target=road(new V(0,108,0),new V(0,108,1000),Style.O6_RAIL,left);int sign=LanePoints.lane(target.rawMesh(),500,slot).sign();
   var source=road(new V(side*260,100,500-sign*214),new V(side*210,100,500-sign*214),Style.O2_ONE,left);all.put(source.id(),source);all.put(target.id(),target);
   var from=pt(all,source,40,0);var to=pt(all,target,500,slot);var rid=id();var ramp=LaneRamps.generate(null,all,rid,id(),new LanePoints.Link(from,to,options(LanePoints.Departure.BRANCH,arrival),null));
   double net=ramp.mesh().last().center().y()-ramp.mesh().first().center().y();check(Math.abs(net-8)<1e-6,"wrong target elevation");check(Math.abs(variation(ramp.mesh())-8)<1e-4,"gratuitous up/down for feasible 8m intake");
   for(int i=1;i<ramp.mesh().samples().size();i++){V d=ramp.mesh().samples().get(i).center().sub(ramp.mesh().samples().get(i-1).center());check(!LaneRampGrade.exceeds(d.y(),d.horizontalLength(),.2),"grade limit bypassed");}
   all.put(rid,ramp);LaneCrossSections.reconcile(all);var h=all.get(target.id());
   check(RoadClearance.contacts(ramp.mesh(),LaneDeck.excludingSlot(LaneDeck.motorOnly(h.mesh()),slot)).stream().noneMatch(RoadClearance.Contact::blocked),"EXTRA/FLOW/MERGE traverses nonselected or opposing lane");
   check(LaneTopology.metadata(h).cuts().stream().anyMatch(LaneSections.Cut::arrival)==(arrival==LanePoints.Arrival.MERGE),"FLOW/EXTRA target wrongly closed");
   check(LanePointCodec.link(LanePointCodec.link(LaneTopology.metadata(ramp).link())).options().arrival()==arrival,"arrival enum codec mismatch");
   var decoded=RoadRecord.load(ramp.header());check(decoded.mesh().samples().size()==ramp.mesh().samples().size(),"saved sample count changed");
   for(int k=0;k<ramp.mesh().samples().size();k++){var before=ramp.mesh().samples().get(k);var after=decoded.mesh().samples().get(k);check(before.center().distance(after.center())<1e-8&&before.left().distance(after.left())<1e-8&&Math.abs(before.distance()-after.distance())<1e-8&&Math.abs(before.halfWidth()-after.halfWidth())<1e-8,"saved geometry changed beyond signed-zero normalization");}

   all.remove(rid);LaneCrossSections.reconcile(all);check(LaneTopology.metadata(all.get(target.id())).cuts().isEmpty(),"deleting leaves target closed");cases++;
   System.out.printf(Locale.ROOT,"  near307 net+8 %s left=%s side=%d slot=%d length=%.2f verticalTravel=%.3f%n",arrival,left,side,slot,ramp.mesh().length(),variation(ramp.mesh()));
  }
 }
 static void detachFlow(){
  for(boolean left:new boolean[]{false,true})for(var style:List.of(Style.O3_ONE,Style.O6_RAIL))for(double d:new double[]{64,120}){
   var all=new LinkedHashMap<UUID,RoadRecord>();var source=road(new V(0,108,0),new V(0,108,900),style,left);all.put(source.id(),source);int sign=LanePoints.lane(source.rawMesh(),450,2).sign();
   var from=pt(all,source,450,2);var to=pt(all,source,450+sign*d,1);var rid=id();var r=LaneRamps.generate(null,all,rid,id(),new LanePoints.Link(from,to,options(LanePoints.Departure.DETACH,LanePoints.Arrival.FLOW),null));all.put(rid,r);LaneCrossSections.reconcile(all);var h=all.get(source.id());
   check(variation(r.mesh())<1e-5,"same-height same-host ordinary merge oscillates");check(!LaneTopology.metadata(r).link().closesTarget(),"FLOW uses protected target closure");
   for(double z=0;z<=d+20;z+=2){double at=450+sign*z;check(LaneSections.active(h.mesh(),at,1),"ordinary target flow interrupted");if(z>34)check(!LaneSections.active(h.mesh(),at,2),"DETACH leaves old outgoing lane active");}
   var protectedMesh=LaneDeck.excludingSlot(LaneDeck.excludingSlot(LaneDeck.motorOnly(h.mesh()),1),2);check(RoadClearance.contacts(r.mesh(),protectedMesh).stream().noneMatch(RoadClearance.Contact::blocked),"samehost FLOW invades unrelated lane");
   var surf=RoadSurface.build(h.mesh(),List.of(r.mesh()),List.of(r.mesh()));int covered=0;
   for(int i=1;i<h.mesh().samples().size();i++){var a=h.mesh().samples().get(i-1);var b=h.mesh().samples().get(i);double station=(a.distance()+b.distance())/2;if(sign*(station-450)<1||sign*(station-450)>d-1)continue;var s=RoadStructures.sample(h.mesh(),station);
    for(double lateral:RoadProfile.layout(h.mesh(),s).dividers()){V p=s.at(lateral,0);var q=RoadQueries.horizontal(r.mesh(),p);
     if(Math.abs(q.lateral())<q.sample().halfWidth()-.3&&Math.abs(q.sample().center().y()-p.y())<.03&&q.sample().distance()>1&&q.sample().distance()<r.mesh().length()-1){
      check(surf.markings().stream().noneMatch(f->JunctionPaint.inside(f.points(),p)),"default divider remains inside welded merge");covered++;
     }
    }
   }
   check(covered>0,"overlap marking test vacuous");
   var data=new RoadData();all.values().forEach(x->data.index.put(new RoadIndex.Built(x)));var batch=new ArrayList<RoadIndex.Built>();var removed=new HashSet<>(Set.of(rid));LaneTopology.reconcile(data,batch,removed);
   var restored=batch.stream().filter(x->x.record.id().equals(source.id())).findFirst().orElseThrow().record;check(LaneTopology.metadata(restored).cuts().isEmpty(),"delete DETACH/FLOW does not restore original lane");cases++;
  }
 }
 public static void main(String[]args){nearbyElevated();detachFlow();System.out.println("Live423ModelValidation: "+cases+" generation/record/ordinary-merge/topology/delete scenes, "+checks+" checks. Production model with explicit Ground/NBT/index; NOT real Minecraft preview/world writes/GPU.");}
}
