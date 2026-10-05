package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import static com.sora.splineroads.world.UnrelatedRamp401Probe.*;

public final class LocalEdits401Validation {
  static int checks;
  static void check(boolean ok,String why){checks++;require(ok,why);}
  public static void main(String[] args){
    UnrelatedRamp401Probe.main(new String[0]);
    var f=fixture();var local=road(new V(10000,100,10000),new V(10000,100,10100));
    var planning=new ArrayList<>(f.data().index.roads.values());planning.add(new RoadIndex.Built(local));
    check(!LaneTopology.needsRefresh(planning,List.of(local.id())),"unrelated stale ports do not demand global stabilization");
    var source=f.source();var p=source.start().position().add(new V(0,2,0));var q=source.end().position().add(new V(0,2,0));
    var moved=new RoadRecord(source.id(),source.owner(),RampJunctions.at(p),RampJunctions.at(q),
        new Node(p,source.start().yaw(),0),new Node(q,source.end().yaw(),0),source.settings());
    boolean blocked=false;
    try{LaneTopology.reconcile(f.data(),new ArrayList<>(List.of(new RoadIndex.Built(moved))),new HashSet<>(Set.of(source.id())));}
    catch(IllegalArgumentException expected){blocked=true;}
    check(blocked,"genuinely moved dependent port is not silently ignored");
    check(f.data().index.roads.get(source.id()).record.equals(source),"rejected plan leaves saved source unchanged");
    var across=road(new V(20,100,100),new V(80,100,100));blocked=false;
    try{LaneTopology.reconcile(f.data(),new ArrayList<>(List.of(new RoadIndex.Built(across))),new HashSet<>());}
    catch(IllegalArgumentException expected){blocked=expected.getMessage().contains("本次道路修改");System.out.println("  local obstacle rejected: "+expected.getMessage());}
    check(blocked,"new road crossing an unchanged ramp is checked, not globally bypassed");
    var deletion=new ArrayList<RoadIndex.Built>();LaneTopology.reconcile(f.data(),deletion,new HashSet<>(Set.of(f.ramp().id())));
    check(deletion.stream().noneMatch(b->b.record.id().equals(f.ramp().id())),"invalid saved ramp itself can be deleted without regenerating it");
    // Support-only batches can contain old connectors without asking to repair their ports.
    var supportBatch=new ArrayList<>(List.of(new RoadIndex.Built(local),new RoadIndex.Built(f.ramp().structures(List.of()))));
    LaneTopology.reconcile(f.data(),supportBatch,new HashSet<>(Set.of(f.ramp().id())));
    var supportRoad=supportBatch.stream().filter(x->x.record.id().equals(f.ramp().id())).findFirst().orElseThrow().record;
    check(supportRoad.mesh().samples().equals(f.ramp().mesh().samples()),"support refresh preserves saved ramp deck instead of regenerating it");
    check(!LaneTopology.needsRefresh(f.data(),new ArrayList<>(f.data().index.roads.values()),List.of(f.ramp().id())),"already stale ports do not force repeated terrain stabilization");
    // Use production RampJunctions here: an unrelated stale junction target stays outside scope.
    var junctionId=id();var junctionRamp=f.ramp().withLanePoints(LaneTopology.metadata(f.ramp()).link(
        new LanePoints.Link(ref(f.source()),LanePoints.Ref.junction(junctionId),LaneTopology.metadata(f.ramp()).link().options(),new V(100,100,140))));
    f.data().index.put(new RoadIndex.Built(junctionRamp));
    var onlyLocal=new ArrayList<>(List.of(new RoadIndex.Built(local)));
    LaneTopology.reconcile(f.data(),onlyLocal,new HashSet<>());
    check(onlyLocal.stream().noneMatch(x->x.record.id().equals(junctionRamp.id())),"unrelated missing junction does not enlist a remote ramp");
    boolean missing=false;try{RampJunctions.reconcile(f.data(),LaneTopology.records(f.data()),Set.of(junctionRamp.id()));}
    catch(IllegalArgumentException expected){missing=expected.getMessage().contains("路口仍被匝道引用");}
    check(missing,"actually affected missing junction still validated by production RampJunctions");
    // Automatic point closure remains protected.
    var d=new RoadData();var a=road(new V(1000,100,0),new V(1000,100,100));var b=pointRoad(road(new V(1200,100,0),new V(1200,100,100)),40);
    d.index.put(new RoadIndex.Built(a));d.index.put(new RoadIndex.Built(b));LaneTopology.initialize(d);a=d.index.roads.get(a.id()).record;
    var end=LaneTopology.metadata(a).points().stream().filter(x->x.origin()==LanePoints.Origin.AUTOMATIC_END).findFirst().orElseThrow();
    var linked=road(new V(1000,100,100),new V(1200,100,40));
    linked=linked.withLanePoints(LaneTopology.metadata(linked).link(new LanePoints.Link(LanePoints.Ref.lane(a.id(),end.id()),ref(b),new LanePoints.Options(LanePoints.Path.AUTO,false,false,24,32),null)));
    d.index.put(new RoadIndex.Built(linked));var extension=road(new V(1000,100,100),new V(1000,100,200));blocked=false;
    try{LaneTopology.reconcile(d,new ArrayList<>(List.of(new RoadIndex.Built(extension))),new HashSet<>());}
    catch(IllegalArgumentException expected){blocked=expected.getMessage().contains("自动尽头");}
    check(blocked,"used automatic endpoint still prevents ordinary closure");
    check(LaneTopology.metadata(d.index.roads.get(a.id()).record).points().contains(end),"blocked closure keeps automatic point");
    System.out.println("LocalEdits401Validation: "+checks+" checks passed (actual planning, no world writes)");
  }
}
