package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Actual planner/record/reconciliation; NBT/world adapters remain explicit test-only code. */
public final class AnyLane405ModelValidation {
  static int checks,cases;static long serial=10000;
  static UUID id(){return new UUID(402,++serial);}
  static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  static RoadRecord road(V a,V b,Style style,boolean left){
    var o=RoadProfile.Options.DEFAULT.traffic(left);var s=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,o,4),1,.35,90).options(o);
    double yaw=RoadPlanner.yaw(b.sub(a).horizontalUnit());return new RoadRecord(id(),id(),RampJunctions.at(a),RampJunctions.at(b),new Node(a,yaw,0),new Node(b,yaw,0),s);
  }
  static LanePoints.Ref point(Map<UUID,RoadRecord> all,UUID road,double station,int slot){var r=all.get(road);var md=LaneTopology.metadata(r);var p=LanePoints.point(id(),LanePoints.Origin.MANUAL,r.rawMesh(),station,slot);var list=new ArrayList<>(md.points());list.add(p);all.put(road,r.withLanePoints(md.points(list)));return LanePoints.Ref.lane(road,p.id());}
  public static void main(String[]args){
    for(var style:List.of(Style.O1_ONE,Style.O3_ONE,Style.O4_ONE,Style.O2_RAIL,Style.O4_RAIL,Style.O6_GREEN,Style.O8_YELLOW))for(boolean left:new boolean[]{false,true})for(int slot=0;slot<RoadProfile.catalog(style).lanes();slot++)for(int vertical:new int[]{-1,1}){
      var all=new LinkedHashMap<UUID,RoadRecord>();var main=road(new V(0,100,0),new V(0,100,1400),style,left);all.put(main.id(),main);
      int count=RoadProfile.catalog(style).lanes(),side=RoadProfile.catalog(style).twoWay()?(slot<count/2?-1:1):(slot<count/2?-1:1),sign=LanePoints.lane(main.rawMesh(),700,slot).sign();double begin=sign>0?200:1200;
      // Outward route, without relying on a broad source-road clearance exemption.
      double x=-side*280,z=begin+sign*450;var target=road(new V(x,100+12*vertical,z),new V(x-side*180,100+12*vertical,z),Style.O1_ONE,left);all.put(target.id(),target);
      var from=point(all,main.id(),begin,slot);var to=point(all,target.id(),20,0);
      var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,32,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
      System.out.println("case "+style+" lane="+slot+" left="+left+" vertical="+vertical);UUID connector=id();var ramp=LaneRamps.generate(null,all,connector,id(),new LanePoints.Link(from,to,options,null));all.put(connector,ramp);LaneCrossSections.reconcile(all);cases++;
      var changed=all.get(main.id());var cut=LaneTopology.metadata(changed).cuts().get(0);check(cut.temporary()&&cut.sign()==sign&&cut.lane()==slot,"cut identity/direction");check(cut.end()>0&&cut.end()<1400,"closure must be finite");check(!LaneSections.active(changed.mesh(),begin+sign*40,slot),"host slot must close");check(LaneSections.active(changed.mesh(),cut.end()+sign*4,slot),"host slot must reopen");LaneReopening.validateRestored(changed.mesh(),slot,ramp.mesh(),connector);
      double closedAt=(cut.begin()+cut.end())/2;
      V removedPoint=LanePoints.lane(main.rawMesh(),closedAt,slot).position();
      check(!RoadQueries.contains(changed.mesh(),removedPoint,0,.1),"closed interior lane remains solid");
      check(Double.isInfinite(RoadQueries.ray(changed.mesh(),removedPoint.add(new V(0,10,0)),new V(0,-1,0),20)),"ray hits invisible old lane");
      for(int other=0;other<count;other++)if(other!=slot){check(LaneSections.active(changed.mesh(),700,other),"other lane closed");check(LanePoints.lane(main.rawMesh(),700,other).position().distance(LanePoints.lane(changed.mesh(),700,other).position())<1e-7,"other lane moved");}
      var roundtrip=RoadRecord.load(changed.header());check(roundtrip.mesh().samples().equals(changed.mesh().samples()),"record adapter roundtrip changed geometry");
      var data=new RoadData();all.values().forEach(r->data.index.put(new RoadIndex.Built(r)));var batch=new ArrayList<RoadIndex.Built>();LaneTopology.reconcile(data,batch,new HashSet<>());
      check(batch.stream().noneMatch(b->b.record.id().equals(connector)&&!b.record.alignment().equals(ramp.alignment())),"unrelated reconcile replanned connector");
      var validHash=changed.header().hashCode();var replay=new LinkedHashMap<>(all);LaneCrossSections.reconcile(replay);check(replay.get(main.id()).header().hashCode()==validHash,"reconciliation is stable");all.remove(connector);LaneCrossSections.reconcile(all);check(LaneTopology.metadata(all.get(main.id())).cuts().isEmpty(),"delete left reservation");
    }
    System.out.println("AnyLane405ModelValidation: "+cases+" planner/record/delete cases, "+checks+" checks PASS. Fake NBT/world adapters, NOT real Minecraft transactions.");
  }
}
