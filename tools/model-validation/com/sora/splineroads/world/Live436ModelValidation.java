package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
public final class Live436ModelValidation {
  static int checks;
  static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
  static void apply(RoadData data,RoadRecord added,UUID deleted){
    var batch=new ArrayList<RoadIndex.Built>();if(added!=null)batch.add(new RoadIndex.Built(added));
    var removed=new HashSet<UUID>();if(deleted!=null)removed.add(deleted);
    LaneTopology.reconcile(data,batch,removed);removed.forEach(data.index.roads::remove);batch.forEach(data.index::put);
  }
  static void sequential(){
    Map<UUID,RoadRecord> all=new LinkedHashMap<>();
    var settings=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,3,0,false);
    var main=Hotfix429ModelValidation.road(new V(0,108,0),new V(0,108,1000),settings);
    var one=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,1,0,false);
    var sourceA=Hotfix429ModelValidation.road(new V(-300,100,100),new V(-220,100,100),one);
    var sourceB=Hotfix429ModelValidation.road(new V(-300,100,600),new V(-220,100,600),one);
    for(var r:List.of(main,sourceA,sourceB))all.put(r.id(),r);
    var a=Hotfix429ModelValidation.point(all,sourceA,40,0);var b=Hotfix429ModelValidation.point(all,main,300,0);
    var c=Hotfix429ModelValidation.point(all,sourceB,40,0);var d=Hotfix429ModelValidation.point(all,all.get(main.id()),800,0);
    var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
    var data=new RoadData();all.values().forEach(r->data.index.put(new RoadIndex.Built(r)));
    var first=LaneRamps.generate(null,all,new UUID(436,10),new UUID(0,1),new LanePoints.Link(a,b,options,null));apply(data,first,null);
    var saved=data.index.roads.get(first.id()).record;var before=saved.header();
    all=LaneTopology.records(data);
    var second=LaneRamps.generate(null,all,new UUID(436,11),new UUID(0,1),new LanePoints.Link(c,d,options,null));all.put(second.id(),second);
    var scope=LaneTopology.editScope(data,all,List.of(new RoadIndex.Built(second)),Set.of());
    check(scope.contains(main.id())&&!scope.contains(first.id()),"new sibling floods through shared host into existing ramp");
    apply(data,second,null);
    var after=data.index.roads.get(first.id()).record;
    check(after.alignment().equals(saved.alignment()),"adding sibling regenerates saved alignment");
    check(after.header().equals(before),"adding noncontact sibling changes saved ramp metadata");
    apply(data,null,second.id());
    check(data.index.roads.get(first.id()).record.header().equals(before),"deleting sibling changes old ramp");
    all=LaneTopology.records(data);var changed=all.get(main.id()).settings(main.settings().options(main.settings().options().outerRail(RoadProfile.OuterRail.OFF)));all.put(main.id(),changed);
    scope=LaneTopology.editScope(data,all,List.of(new RoadIndex.Built(changed)),Set.of(main.id()));
    check(scope.contains(first.id()),"real host edit lost dependent validation");
    System.out.println("SEQUENTIAL436 PASS: add two ramps sharing host, preserve first, delete second; actual topology planner, no world writes");
  }
  public static void main(String[]args){sequential();System.out.println("Live436ModelValidation: "+checks+" checks PASS");}
}
