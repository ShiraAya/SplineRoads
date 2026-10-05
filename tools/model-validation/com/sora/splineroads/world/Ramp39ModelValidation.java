package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Executes production server planning/codec methods with explicitly test-only world/NBT adapters. */
public final class Ramp39ModelValidation {
  private static int checks;private static long id=100;
  private static void check(boolean ok,String m){checks++;if(!ok)throw new AssertionError(m);}
  private static void near(double a,double b,double eps,String m){check(Math.abs(a-b)<eps,m+": "+a+" / "+b);}
  private static UUID id(){return new UUID(0,++id);}
  private static RoadRecord road(V from,V to,Style style){
    var s=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,RoadProfile.Options.DEFAULT,4),1,.35,90);
    var d=to.sub(from);var n1=new Node(from,RoadPlanner.yaw(d.horizontalUnit()),d.y()/d.horizontalLength());var n2=new Node(to,n1.yaw(),n1.grade());
    return new RoadRecord(id(),new UUID(0,1),RampJunctions.at(from),RampJunctions.at(to),n1,n2,s);
  }
  private static LanePoints.Ref point(Map<UUID,RoadRecord> all,UUID road,double station,int lane){var r=all.get(road);var p=LanePoints.point(id(),LanePoints.Origin.MANUAL,r.mesh(),station,lane);var points=new ArrayList<>(LaneTopology.metadata(r).points());points.add(p);all.put(road,r.withLanePoints(LaneTopology.metadata(r).points(points)));return LanePoints.Ref.lane(road,p.id());}
  private static RoadRecord ramp(Map<UUID,RoadRecord> all,LanePoints.Ref a,LanePoints.Ref b,LanePoints.Options o){
    var r=LaneRamps.generate(null,all,id(),new UUID(0,1),new LanePoints.Link(a,b,o,null));all.put(r.id(),r);LaneCrossSections.reconcile(all);LaneTopology.assignPriorities(all);return all.get(r.id());
  }
  private static LanePoints.Options options(LanePoints.Departure d,LanePoints.Arrival a){return new LanePoints.Options(LanePoints.Path.AUTO,d,a,32,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);}
  public static void main(String[] args){codec();planning();automatic();System.out.println("Ramp39ModelValidation: "+checks+" checks passed; real LaneRamps/LaneTopology/LanePointCodec/RoadRecord, test-only NBT/world adapters; NO game/client/world-write claim");}
  private static void codec(){
    for(var departure:LanePoints.Departure.values())for(var arrival:LanePoints.Arrival.values())for(var elevation:LanePoints.Elevation.values())for(var landing:LanePoints.Landing.values())for(var path:LanePoints.Path.values()){
      var o=new LanePoints.Options(path,departure,arrival,32,40,elevation,landing);check(o.equals(LanePointCodec.options(LanePointCodec.options(o))),"typed options roundtrip");}
    var legacy=LanePointCodec.options(new LanePoints.Options(LanePoints.Path.LEFT_LOOP,false,true,24,32));for(String name:List.of("Departure","Arrival","Elevation","Landing"))legacy.remove(name);
    check(LanePointCodec.options(legacy).departure()==LanePoints.Departure.BRANCH,"legacy false does not delete lanes");check(LanePointCodec.options(legacy).arrival()==LanePoints.Arrival.EXTRA,"legacy true retains extra");
    var r=road(new V(0,100,0),new V(0,100,400),Style.O2_ONE);var p=LanePoints.point(id(),LanePoints.Origin.MANUAL,r.mesh(),100,1);var c=new LaneSections.Cut(id(),1,1,100,300,32,id());
    r=r.withLanePoints(new LanePoints.Data(List.of(p),null,List.of(),0,List.of(c)));
    var restored=RoadRecord.load(r.header());check(restored.settings().options().lanePoints().equals(r.settings().options().lanePoints()),"lane metadata schema survives full record save/load");
    near(restored.mesh().samples().get(500).halfWidth(),r.mesh().samples().get(500).halfWidth(),1e-8,"physical lane section survives record roundtrip");
  }
  private static void planning(){
    var all=new LinkedHashMap<UUID,RoadRecord>();var main=road(new V(0,100,0),new V(0,100,800),Style.O2_ONE);var out=road(new V(-240,100,420),new V(-360,100,420),Style.O1_ONE);var in=road(new V(-300,100,560),new V(-240,100,560),Style.O1_ONE);
    for(var r:List.of(main,out,in))all.put(r.id(),r);
    var a=point(all,main.id(),100,1);var b=point(all,out.id(),20,0);var ia=point(all,in.id(),40,0);var ib=point(all,main.id(),600,1);
    var depart=ramp(all,a,b,options(LanePoints.Departure.DETACH,LanePoints.Arrival.MERGE));
    check(!LaneSections.active(all.get(main.id()).mesh(),300,1),"production generate physically removes downstream source lane");
    near(LanePoints.lane(all.get(main.id()).mesh(),300,0).position().x(),2,1e-6,"continuing lane remains in place");
    var incoming=ramp(all,ia,ib,options(LanePoints.Departure.BRANCH,LanePoints.Arrival.REPLACE));
    check(LaneSections.active(all.get(main.id()).mesh(),650,1),"replacement restores downstream slot");
    check(!LaneSections.active(all.get(main.id()).mesh(),300,1),"replacement does not reinstate upstream vacancy");
    for(var r:List.of(depart,incoming))LaneRamps.validate(all.get(r.id()).mesh(),all,r.id(),LaneTopology.metadata(r).link());checks+=2;
    var data=new RoadData();for(var r:all.values())data.index.put(new RoadIndex.Built(r));
    check(LaneTopology.dependents(data,Set.of(depart.id())).contains(incoming.id()),"delete closure includes replacement dependent");
    var changed=new ArrayList<RoadIndex.Built>();var deleted=new HashSet<UUID>(Set.of(depart.id(),incoming.id()));LaneTopology.reconcile(data,changed,deleted);
    var restored=changed.stream().map(built->built.record).filter(r->r.id().equals(main.id())).findFirst().orElseThrow();check(LaneTopology.metadata(restored).cuts().isEmpty(),"deletion reconciliation restores original section");near(restored.mesh().first().halfWidth(),4.5,1e-8,"restored width");
    // Metadata edits must never reset the port-fitted per-sample widths.
    var changedPaint=depart.settings(depart.settings().options(depart.settings().options().hideArrows(true)));
    check(changedPaint.alignment().equals(depart.alignment()),"paint-only edit preserves fitted alignment widths");
    System.out.printf(Locale.ROOT,"  production DETACH + REPLACE: outgoing %.1f m, incoming %.1f m, host retains lane 0; deletion restores both lanes%n",depart.mesh().length(),incoming.mesh().length());
  }
  private static void automatic(){
    var data=new RoadData();var main=road(new V(0,100,0),new V(0,100,200),Style.O2_ONE);data.index.put(new RoadIndex.Built(main));LaneTopology.initialize(data);
    check(LaneTopology.metadata(data.index.roads.get(main.id()).record).points().size()==4,"automatic points on both free ends, each lane");
    var other=road(new V(0,100,200),new V(0,100,400),Style.O2_ONE);data.index.put(new RoadIndex.Built(other));LaneTopology.initialize(data);
    check(LaneTopology.metadata(data.index.roads.get(main.id()).record).points().size()==2,"normal connection removes old free end points");
    check(LaneTopology.metadata(data.index.roads.get(other.id()).record).points().size()==2,"normal connection never adds seam points");
  }
}
