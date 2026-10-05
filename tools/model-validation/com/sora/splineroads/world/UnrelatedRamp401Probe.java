package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Regression uses actual LaneTopology/LaneRamps. World commit is NOT emulated. */
public final class UnrelatedRamp401Probe {
  static long serial=90000;
  static UUID id(){return new UUID(41,++serial);}
  static RoadRecord road(V a,V b){
    var settings=new Settings(Mode.STRAIGHT,Style.O1_ONE,5,1,.35,90);
    var direction=b.sub(a).horizontalUnit();
    return new RoadRecord(id(),new UUID(0,1),RampJunctions.at(a),RampJunctions.at(b),
        new Node(a,RoadPlanner.yaw(direction),0),new Node(b,RoadPlanner.yaw(direction),0),settings);
  }
  static RoadRecord pointRoad(RoadRecord road,double s){
    var p=LanePoints.point(id(),LanePoints.Origin.MANUAL,road.mesh(),s,0);
    return road.withLanePoints(LaneTopology.metadata(road).points(List.of(p)));
  }
  static LanePoints.Ref ref(RoadRecord road){return LanePoints.Ref.lane(road.id(),LaneTopology.metadata(road).points().get(0).id());}
  record Fixture(RoadData data,RoadRecord source,RoadRecord target,RoadRecord ramp){}
  static Fixture fixture(){
    var source=pointRoad(road(new V(0,100,0),new V(0,100,80)),60);
    var target=pointRoad(road(new V(90,100,150),new V(170,100,150)),20);
    var options=new LanePoints.Options(LanePoints.Path.DIRECT,false,false,24,32);
    // Valid reference data, but a legacy alignment whose port/path constraints no longer agree.
    // No ordinary edit far away is an instruction to regenerate this connector.
    var ramp=road(new V(0,100,60),new V(100,100,140));
    ramp=ramp.withLanePoints(LaneTopology.metadata(ramp).link(new LanePoints.Link(ref(source),ref(target),options,null)));
    var data=new RoadData();for(var r:List.of(source,target,ramp))data.index.put(new RoadIndex.Built(r));
    return new Fixture(data,source,target,ramp);
  }
  static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
  public static void main(String[] args){
    boolean baseline=args.length>0&&args[0].equals("baseline");
    var f=fixture();var local=road(new V(10000,100,10000),new V(10000,100,10100));
    var batch=new ArrayList<RoadIndex.Built>();batch.add(new RoadIndex.Built(local));var removed=new HashSet<UUID>();
    try{
      LaneTopology.reconcile(f.data(),batch,removed);
      require(!baseline,"baseline unexpectedly accepted fixture");
      require(batch.stream().noneMatch(b->b.record.id().equals(f.ramp().id())),"unrelated legacy ramp must not enter transaction");
      require(batch.stream().filter(b->b.record.id().equals(local.id())).allMatch(b->LaneTopology.metadata(b.record).link()==null),"ordinary road stays ordinary");
      System.out.println("PATCH PASS: unrelated create ignores invalid remote ramp; no remote records rewritten");
    }catch(IllegalArgumentException failure){
      if(!baseline)throw failure;
      System.out.println("BASELINE REPRODUCED: unrelated create rejected by remote ramp: "+failure.getMessage());return;
    }
    f.data().index.put(new RoadIndex.Built(local));
    var edited=local.settings(local.settings().options(local.settings().options().hideArrows(true)));
    batch=new ArrayList<>(List.of(new RoadIndex.Built(edited)));removed=new HashSet<>(Set.of(local.id()));
    LaneTopology.reconcile(f.data(),batch,removed);
    require(batch.stream().noneMatch(b->b.record.id().equals(f.ramp().id())),"remote ramp not enlisted on normal update");
    batch=new ArrayList<>();removed=new HashSet<>(Set.of(local.id()));LaneTopology.reconcile(f.data(),batch,removed);
    require(batch.stream().noneMatch(b->b.record.id().equals(f.ramp().id())),"remote ramp not enlisted on normal deletion");
    require(f.data().index.roads.get(f.ramp().id()).record.equals(f.ramp()),"read-only planning never alters saved original");
    System.out.println("PATCH PASS: unrelated normal update + deletion also pass; saved remote ramp unchanged");
  }
}
