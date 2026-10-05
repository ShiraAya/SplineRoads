package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Actual planner/record/reconciliation; NBT/world adapters remain explicit test-only code. */
public final class Bidirectional402ModelValidation {
  static int checks,cases;static long serial=10000;
  static UUID id(){return new UUID(402,++serial);}
  static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  static RoadRecord road(V a,V b,Style style,boolean left){
    var o=RoadProfile.Options.DEFAULT.traffic(left);var s=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,o,4),1,.35,90).options(o);
    double yaw=RoadPlanner.yaw(b.sub(a).horizontalUnit());return new RoadRecord(id(),id(),RampJunctions.at(a),RampJunctions.at(b),new Node(a,yaw,0),new Node(b,yaw,0),s);
  }
  static LanePoints.Ref point(Map<UUID,RoadRecord> all,UUID road,double station,int slot){var r=all.get(road);var md=LaneTopology.metadata(r);var p=LanePoints.point(id(),LanePoints.Origin.MANUAL,r.rawMesh(),station,slot);var list=new ArrayList<>(md.points());list.add(p);all.put(road,r.withLanePoints(md.points(list)));return LanePoints.Ref.lane(road,p.id());}
  public static void main(String[]args){
    for(var style:List.of(Style.O2_RAIL,Style.O4_RAIL,Style.O6_GREEN,Style.O8_YELLOW))for(boolean left:new boolean[]{false,true})for(int side:new int[]{-1,1}){
      var all=new LinkedHashMap<UUID,RoadRecord>();var main=road(new V(0,100,0),new V(0,100,1400),style,left);all.put(main.id(),main);
      int count=RoadProfile.catalog(style).lanes(),slot=side<0?count/2-1:count-1,sign=LanePoints.lane(main.rawMesh(),700,slot).sign();double begin=sign>0?200:1200;
      // Outward route, without relying on a broad source-road clearance exemption.
      double x=-side*280,z=begin+sign*450;var target=road(new V(x,100,z),new V(x-side*180,100,z),Style.O1_ONE,left);all.put(target.id(),target);
      var from=point(all,main.id(),begin,slot);var to=point(all,target.id(),20,0);
      var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.DETACH,LanePoints.Arrival.MERGE,32,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
      UUID connector=id();var ramp=LaneRamps.generate(null,all,connector,id(),new LanePoints.Link(from,to,options,null));all.put(connector,ramp);LaneCrossSections.reconcile(all);cases++;
      var changed=all.get(main.id());var cut=LaneTopology.metadata(changed).cuts().get(0);check(cut.sign()==sign&&cut.lane()==slot,"cut identity/direction");check(!LaneSections.active(changed.mesh(),sign>0?1399:1,slot),"lane remains live downstream");
      for(int other=0;other<count;other++)if(other!=slot){check(LaneSections.active(changed.mesh(),700,other),"other lane closed");check(LanePoints.lane(main.rawMesh(),700,other).position().distance(LanePoints.lane(changed.mesh(),700,other).position())<1e-7,"other lane moved");}
      var roundtrip=RoadRecord.load(changed.header());check(roundtrip.mesh().samples().equals(changed.mesh().samples()),"record adapter roundtrip changed geometry");
      var data=new RoadData();all.values().forEach(r->data.index.put(new RoadIndex.Built(r)));var batch=new ArrayList<RoadIndex.Built>();LaneTopology.reconcile(data,batch,new HashSet<>());
      check(batch.stream().noneMatch(b->b.record.id().equals(connector)&&!b.record.alignment().equals(ramp.alignment())),"unrelated reconcile replanned connector");
      all.remove(connector);LaneCrossSections.reconcile(all);check(LaneTopology.metadata(all.get(main.id())).cuts().isEmpty(),"delete left reservation");
    }
    System.out.println("Bidirectional402ModelValidation: "+cases+" planner/record/delete cases, "+checks+" checks PASS. Fake NBT/world adapters, NOT real Minecraft transactions.");
  }
}
