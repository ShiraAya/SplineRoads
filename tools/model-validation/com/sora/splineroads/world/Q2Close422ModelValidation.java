package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
/** Production lane drop/replacement/codec/reconcile. World/NBT are explicit adapters. */
public final class Q2Close422ModelValidation {
 static int checks,cases;static long serial;static UUID id(){return new UUID(422,++serial);}static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}static void reject(Runnable r,String m){try{r.run();}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError(m);}
 public static void main(String[] args){
  for(var style:List.of(Style.O2_ONE,Style.O3_ONE,Style.O4_RAIL,Style.O6_GREEN,Style.H6_RAIL))for(boolean left:new boolean[]{false,true})for(int side:new int[]{-1,1}){
   var all=new LinkedHashMap<UUID,RoadRecord>();var host=Arrival417ModelValidation.road(new V(0,100,0),new V(0,100,1400),style,left);all.put(host.id(),host);var c=RoadProfile.catalog(style);int slot=c.twoWay()?(side<0?c.lanes()/2-1:c.lanes()-1):(side<0?0:c.lanes()-1);int sign=LanePoints.lane(host.rawMesh(),700,slot).sign();double at=sign>0?200:1200;
   var ref=Arrival417ModelValidation.point(all,host,at,slot);host=all.get(host.id());var md=LaneTopology.metadata(host);var point=LaneTopology.point(host,ref.point());var ps=new ArrayList<>(md.points());ps.set(ps.indexOf(point),point.merge(32));host=host.withLanePoints(md.points(ps));all.put(host.id(),host);LaneCrossSections.reconcile(all);host=all.get(host.id());cases++;
   check(all.size()==1,"lane drop created a fake ramp");var cut=LaneTopology.metadata(host).cuts().get(0);check(cut.connection().equals(point.id())&&cut.replacement()==null,"manual point does not own its vacancy");
   check(!LaneSections.active(host.mesh(),at+sign*80,slot),"drop left lane driveable");check(LaneSections.active(host.mesh(),at-sign*8,slot),"upstream closed prematurely");check(!LaneMerge.guides(host.mesh()).isEmpty(),"actual inward merge guide absent");
   for(int i=0;i<c.lanes();i++)if(i!=slot){check(LaneSections.active(host.mesh(),at+sign*80,i),"unrelated lane closed");check(LanePoints.lane(host.mesh(),at+sign*80,i).position().distance(LanePoints.lane(host.rawMesh(),at+sign*80,i).position())<1e-7,"retained lane moved");}
   var loaded=RoadRecord.load(host.header());check(LaneTopology.point(loaded,point.id()).mergeLength()==32,"manual definition not saved");check(loaded.mesh().samples().equals(host.mesh().samples()),"load changed physical taper");
   var end=Arrival417ModelValidation.point(all,host,700,slot);var feeder=Arrival417ModelValidation.road(new V(side*-360,100,700-sign*350),new V(side*-280,100,700-sign*350),Style.O1_ONE,left);all.put(feeder.id(),feeder);var from=Arrival417ModelValidation.point(all,feeder,60,0);
   var opts=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.REPLACE,32,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);var rid=id();RoadRecord ramp;
   try{ramp=LaneRamps.generate(null,all,rid,id(),new LanePoints.Link(from,end,opts,null));}catch(IllegalArgumentException e){throw new AssertionError(style+" side="+side+" left="+left+" "+e.getMessage(),e);}all.put(rid,ramp);LaneCrossSections.reconcile(all);host=all.get(host.id());
   cut=LaneTopology.metadata(host).cuts().get(0);check(rid.equals(cut.replacement()),"REPLACE cannot consume standalone vacancy");check(LaneSections.active(host.mesh(),700+sign*40,slot),"replacement failed to restore downstream width");check(!LaneSections.active(host.mesh(),at+sign*80,slot),"replacement removed upstream taper");
   all.remove(rid);LaneCrossSections.reconcile(all);host=all.get(host.id());check(LaneTopology.metadata(host).cuts().get(0).replacement()==null,"deleting arrival destroys independent drop");check(!LaneSections.active(host.mesh(),sign>0?1390:10,slot),"vacancy not reestablished");
   md=LaneTopology.metadata(host);ps=new ArrayList<>(md.points());point=LaneTopology.point(host,ref.point());ps.set(ps.indexOf(point),point.merge(0));all.put(host.id(),host.withLanePoints(md.points(ps)));LaneCrossSections.reconcile(all);check(LaneTopology.metadata(all.get(host.id())).cuts().isEmpty(),"cancel drop does not restore host");
   System.out.println("  independent vacancy -> REPLACE -> delete arrival -> cancel: "+style+" side="+side+" left="+left);
  }
  guards();slopedPorts();System.out.println("Q2Close422ModelValidation: "+cases+" actual planning/codec/drop/replacement cases / "+checks+" checks; explicit NBT/world adapters, no game world writes.");
 }
 static void slopedPorts(){
  for(int sign:new int[]{-1,1})for(double offset:new double[]{0,100000}){
    var settings=new Settings(Mode.STRAIGHT,Style.O1_ONE,4,1,.35,90);
    V a=new V(offset-100,100+sign*6,offset),b=new V(offset,100,offset),c=new V(offset+600,100,offset),d=new V(offset+700,100+sign*6,offset);
    var first=new RoadRecord(id(),id(),RampJunctions.at(a),RampJunctions.at(b),new Node(a,-90,-sign*.06),new Node(b,-90,-sign*.06),settings);
    var last=new RoadRecord(id(),id(),RampJunctions.at(c),RampJunctions.at(d),new Node(c,-90,sign*.06),new Node(d,-90,sign*.06),settings);
    var all=new LinkedHashMap<UUID,RoadRecord>();all.put(first.id(),first);all.put(last.id(),last);var from=Arrival417ModelValidation.point(all,first,90,0);var to=Arrival417ModelValidation.point(all,last,10,0);
    var options=new LanePoints.Options(LanePoints.Path.DIRECT,LanePoints.Departure.BRANCH,LanePoints.Arrival.MERGE,24,24,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
    var ramp=LaneRamps.generate(null,all,id(),id(),new LanePoints.Link(from,to,options,null));double low=ramp.mesh().samples().stream().mapToDouble(p->p.center().y()).min().orElseThrow(),high=ramp.mesh().samples().stream().mapToDouble(p->p.center().y()).max().orElseThrow();
    check(high-low<1,"long actual connector has needless several-block tangent sag "+low+".."+high);check(Grade421ModelValidation.grade(ramp.mesh())<=.20,"localized grade correction bypasses ceiling");cases++;
  }
 }
 static void guards(){
  var h=Arrival417ModelValidation.road(new V(0,100,0),new V(0,100,600),Style.O3_ONE,false);var p=LanePoints.point(id(),LanePoints.Origin.MANUAL,h.rawMesh(),100,1).merge(32);var baseline=h;reject(()->LaneMerge.event(baseline.rawMesh(),p),"interior lane drop allowed");
  var single=Arrival417ModelValidation.road(new V(0,100,0),new V(0,100,600),Style.O2_RAIL,false);var sp=LanePoints.point(id(),LanePoints.Origin.MANUAL,single.rawMesh(),300,0).merge(32);reject(()->LaneMerge.event(single.rawMesh(),sp),"opposing lane accepted as merge receiver");
  var near=LanePoints.point(id(),LanePoints.Origin.MANUAL,h.rawMesh(),595,2).merge(32);reject(()->LaneMerge.event(baseline.rawMesh(),near),"no downstream room allowed");reject(()->p.merge(Double.NaN),"nonfinite merge length allowed");reject(()->p.merge(-5),"negative merge length allowed");
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(h.id(),h);var r=Arrival417ModelValidation.point(all,h,100,2);h=all.get(h.id());var md=LaneTopology.metadata(h);var pts=new ArrayList<>(md.points());pts.set(0,pts.get(0).merge(32));var block=new LaneSections.Cut(id(),1,1,80,180,32,null,true,false,true);h=h.withLanePoints(md.points(pts).cuts(List.of(block)));var test=h;reject(()->LaneMerge.event(test.mesh(),LaneTopology.point(test,r.point())),"closed receiver accepted");
 }
}
