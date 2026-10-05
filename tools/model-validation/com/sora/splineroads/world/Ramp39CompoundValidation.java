package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Same lane/crossing topology as the drawing; coordinates are test fixtures, not a game template.
 * Existing feeder IV is authored before II so the OVER solver has a real obstacle to cross.
 * No hard-coded curve control points, mesh heights or accepted collision failures. */
public final class Ramp39CompoundValidation {
  static int checks;static long seq=40000;static double scale=1,angle=0,origin=0;
  static UUID id(){return new UUID(0,++seq);}
  static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
  static V position(double x,double z){x*=scale;z*=scale;return new V(origin+x*Math.cos(angle)-z*Math.sin(angle),100,origin+x*Math.sin(angle)+z*Math.cos(angle));}
  static RoadRecord road(double x,double z,double x2,double z2,Style style){
    V a=position(x,z),b=position(x2,z2);double yaw=RoadPlanner.yaw(b.sub(a).horizontalUnit());
    var s=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,RoadProfile.Options.DEFAULT,4),1,.35,90);
    return new RoadRecord(id(),new UUID(0,1),RampJunctions.at(a),RampJunctions.at(b),new Node(a,yaw,0),new Node(b,yaw,0),s);
  }
  static LanePoints.Ref point(Map<UUID,RoadRecord> all,UUID id,double station,int lane){
    var r=all.get(id);var p=LanePoints.point(id(),LanePoints.Origin.MANUAL,r.mesh(),station,lane);
    var l=new ArrayList<>(LaneTopology.metadata(r).points());l.add(p);all.put(id,r.withLanePoints(LaneTopology.metadata(r).points(l)));return LanePoints.Ref.lane(id,p.id());
  }
  static RoadRecord generate(Map<UUID,RoadRecord> all,LanePoints.Ref a,LanePoints.Ref b,LanePoints.Departure d,LanePoints.Arrival ar,LanePoints.Elevation e){
    var o=new LanePoints.Options(LanePoints.Path.AUTO,d,ar,Math.max(8,(ar==LanePoints.Arrival.REPLACE?32:96)*scale),Math.max(8,40*scale),e,LanePoints.Landing.EXACT);
    var r=LaneRamps.generate(null,all,id(),new UUID(0,1),new LanePoints.Link(a,b,o,null));
    all.put(r.id(),r);LaneCrossSections.reconcile(all);LaneTopology.assignPriorities(all);return all.get(r.id());
  }
  public static void main(String[] args){
    for(double[] fixture:List.of(new double[]{1,0,0},new double[]{.5,0,0},new double[]{.5,.6,100000})){
      scale=fixture[0];angle=fixture[1];origin=fixture[2];run();
    }
    System.out.println("Ramp39CompoundValidation: "+checks+" checks passed; in-memory production planner/reconcile only, NOT Minecraft construction/rendering");
  }
  static void run(){
    var all=new LinkedHashMap<UUID,RoadRecord>();
    var main=road(0,0,0,2000,Style.O2_ONE);var outII=road(-850,1050,-950,1050,Style.O1_ONE);var outI=road(700,1100,800,1100,Style.O1_ONE);var feeder=road(-950,500,-45,500,Style.O1_ONE);
    var taper=road(0,-300,0,0,Style.O3_ONE);
    var ends=new RoadTransitions.Ends(RoadTransitions.Section.of(taper.settings()),RoadTransitions.Section.of(main.settings()),true,0,0,null);
    taper=taper.settings(taper.settings().options(taper.settings().options().ends(ends)));
    for(var r:List.of(taper,main,outI,outII,feeder))all.put(r.id(),r);
    check(RoadProfile.layout(taper.mesh(),taper.mesh().first()).catalog().lanes()==3,"approach starts with three lanes");
    check(RoadProfile.layout(taper.mesh(),taper.mesh().last()).catalog().lanes()==2,"approach ends with two lanes");
    var a=point(all,main.id(),150*scale,1);var b=point(all,outII.id(),20*scale,0);
    var ii=generate(all,a,b,LanePoints.Departure.DETACH,LanePoints.Arrival.MERGE,LanePoints.Elevation.OVER);
    var crest=ii.mesh().samples().stream().filter(s->s.center().y()>105.099999).findFirst().orElseThrow();
    var split=point(all,ii.id(),crest.distance(),0);var ib=point(all,outI.id(),20*scale,0);
    var i=generate(all,split,ib,LanePoints.Departure.BRANCH,LanePoints.Arrival.MERGE,LanePoints.Elevation.OVER);
    var ca=point(all,feeder.id(),880*scale,0);var cb=point(all,main.id(),1500*scale,1);
    var iv=generate(all,ca,cb,LanePoints.Departure.BRANCH,LanePoints.Arrival.REPLACE,LanePoints.Elevation.AUTO);
    for(var r:List.of(ii,i,iv))LaneRamps.validate(all.get(r.id()).mesh(),all,r.id(),LaneTopology.metadata(r).link());checks+=3;
    var host=all.get(main.id());check(!LaneSections.active(host.mesh(),800*scale,1),"lane II no longer continues in host");check(LaneSections.active(host.mesh(),1700*scale,1),"incoming IV restores lane B");
    V continued=LanePoints.lane(main.mesh(),800*scale,0).position();check(LanePoints.lane(host.mesh(),800*scale,0).position().distance(continued)<1e-6,"lane I/A stays fixed");
    var upperI=RoadClearance.contacts(i.mesh(),host.mesh());check(!upperI.isEmpty(),"branch I actually crosses main lane A");
    for(var c:upperI)check(!c.blocked()&&c.ours().y()>c.other().y(),"branch I above main with real deck clearance");
    var upperII=RoadClearance.contacts(ii.mesh(),feeder.mesh());check(!upperII.isEmpty(),"branch II actually crosses incoming IV feeder");
    for(var c:upperII)check(!c.blocked()&&c.ours().y()>c.other().y(),"branch II above feeder IV with real deck clearance");
    var data=new RoadData();all.values().forEach(r->data.index.put(new RoadIndex.Built(r)));
    var built=new ArrayList<RoadIndex.Built>();var removed=new HashSet<UUID>();LaneTopology.reconcile(data,built,removed);
    removed.forEach(data.index.roads::remove);built.forEach(data.index::put);
    check(data.index.roads.size()==all.size(),"reconcile keeps all real roads");
    var reloaded=new RoadData();data.index.roads.values().forEach(r->reloaded.index.put(RoadIndex.Built.loading(RoadRecord.load(r.record.header()))));LaneTopology.initialize(reloaded);
    check(!LaneTopology.needsRefresh(reloaded.index.roads.values()),"reload keeps all linked lane ports stable");
    var delete=new LinkedHashSet<>(Set.of(ii.id()));delete.addAll(LaneTopology.dependents(reloaded,delete));
    check(delete.contains(i.id())&&delete.contains(iv.id()),"cascade closure includes branch and replacement");
    var restored=new ArrayList<RoadIndex.Built>();LaneTopology.reconcile(reloaded,restored,delete);
    var recovered=restored.stream().map(v->v.record).filter(v->v.id().equals(main.id())).findFirst().orElseThrow();
    check(LaneTopology.metadata(recovered).cuts().isEmpty(),"deleting the connected ramp group restores original mainline");
    System.out.printf(Locale.ROOT,"  compound scale=%.2f rotation=%.2f origin=%.0f: 3->2, DETACH, fork, I over A, II over IV, REPLACE, reconcile/reload/delete; ramps %.1f / %.1f / %.1f m%n",scale,angle,origin,ii.mesh().length(),i.mesh().length(),iv.mesh().length());
  }
}
