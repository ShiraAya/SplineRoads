package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
public final class Live437ModelValidation {
 static int checks;
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void expansion(){
  for(boolean left:new boolean[]{false,true})for(int sign:new int[]{-1,1}){
   var settings=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,3,3,left);
   var road=Hotfix429ModelValidation.road(new V(0,100,0),new V(0,100,300),settings);var raw=road.mesh();
   var lane=LaneSections.live(raw,150).lanes().stream().filter(l->l.sign()==sign&&LaneSections.edge(raw,150,l.index())).findFirst().orElseThrow();
   var p=LanePoints.point(new UUID(437,left?sign+3:sign+1),LanePoints.Origin.MANUAL,raw,150,lane.index()).merge(-32);
   var md=LaneTopology.metadata(road);road=road.withLanePoints(md.points(List.of(p)));
   var all=new LinkedHashMap<UUID,RoadRecord>();all.put(road.id(),road);LaneCrossSections.reconcile(all);var grown=all.get(road.id());
   check(LaneSections.live(grown.mesh(),150+sign*60).count(sign)==4,"expansion missing downstream lane");
   check(LaneSections.live(grown.mesh(),150-sign*60).count(sign)==3,"expansion changes upstream count");
   check(LaneSections.live(grown.mesh(),150+sign*60).count(-sign)==3,"expansion changes opposite carriageway");
   var loaded=RoadRecord.load(grown.save());check(loaded.save().equals(grown.save()),"expansion does not survive codec");
   all.put(road.id(),loaded);LaneCrossSections.reconcile(all);check(all.get(road.id()).header().equals(grown.header()),"expansion slot changes on reload");
   var saved=all.get(road.id());all.put(road.id(),saved.withLanePoints(LaneTopology.metadata(saved).points(List.of(p.merge(0)))));LaneCrossSections.reconcile(all);
   check(all.get(road.id()).mesh().samples().equals(raw.samples()),"cancel expansion leaves widened road");
  }
 }
 static void modeSwitch(){
  var host=Hotfix429ModelValidation.road(new V(0,108,0),new V(0,108,700),Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,2,0,false));
  var source=Hotfix429ModelValidation.road(new V(-160,100,100),new V(-160,100,300),Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,1,0,false));
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(host.id(),host);all.put(source.id(),source);
  var from=Hotfix429ModelValidation.point(all,source,80,0);var to=Hotfix429ModelValidation.point(all,host,480,1);UUID id=new UUID(437,99);
  var add=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.ADD,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  var ramp=LaneRamps.generate(null,all,id,new UUID(0,1),new LanePoints.Link(from,to,add,null));all.put(id,ramp);LaneCrossSections.reconcile(all);
  var widened=all.get(host.id());var addition=LaneAdditions.owned(widened.mesh(),id);double at=addition.station()-addition.sign()*16;
  check(!RoadQueries.contains(widened.mesh(),LanePoints.lane(widened.mesh(),at,addition.slot()).position(),0,.01),"host widening blocks incoming ramp");
  check(LaneClosureLandscape.plan(widened.mesh(),new RoadStructures.Ground(){public double top(double x,double z,double y){return y-.2;}public boolean joined(V p){return false;}public boolean blocked(RoadStructures.Part p){return false;}}).isEmpty(),"new incoming lane filled by phantom planter");
  var extra=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.EXTRA,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  var edited=LaneRamps.generate(null,all,id,new UUID(0,1),new LanePoints.Link(from,to,extra,null));all.put(id,edited);LaneCrossSections.reconcile(all);
  check(LaneTopology.metadata(all.get(host.id())).additions().isEmpty(),"ADD to EXTRA retains old host taper");
  check(LaneTopology.metadata(all.get(host.id())).cuts().stream().noneMatch(c->c.connection().equals(id)),"ADD to EXTRA retains old slot cut");
 }
 static void derivedScope(){
  var settings=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,3,0,false);
  var host=Hotfix429ModelValidation.road(new V(0,108,0),new V(0,108,500),settings);
  var source=Hotfix429ModelValidation.road(new V(-200,100,0),new V(-100,100,0),settings);
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(host.id(),host);all.put(source.id(),source);
  var a=Hotfix429ModelValidation.point(all,source,50,0);var b=Hotfix429ModelValidation.point(all,host,200,0);
  var link=new LanePoints.Link(a,b,LanePoints.Options.DEFAULT,null);
  var sibling=Hotfix429ModelValidation.road(new V(-150,100,0),new V(0,108,200),settings);sibling=sibling.withLanePoints(LanePoints.Data.EMPTY.link(link));all.put(sibling.id(),sibling);
  var data=new RoadData();all.values().forEach(r->data.index.put(new RoadIndex.Built(r)));
  host=all.get(host.id());var cut=new LaneSections.Cut(new UUID(437,88),1,1,180,250,32,null,true,false,true);
  var changed=host.withLanePoints(LaneTopology.metadata(host).cuts(List.of(cut)));all.put(host.id(),changed);
  var scope=LaneTopology.editScope(data,all,List.of(new RoadIndex.Built(changed)),Set.of(host.id()));
  check(!scope.contains(sibling.id()),"derived second pass reroutes adjacent sibling");
  var authored=host.settings(host.settings().options(host.settings().options().outerRail(RoadProfile.OuterRail.OFF)));all.put(host.id(),authored);
  check(LaneTopology.editScope(data,all,List.of(new RoadIndex.Built(authored)),Set.of(host.id())).contains(sibling.id()),"authored host edit skips dependencies");
 }
  static void closeSiblings(){
    Map<UUID,RoadRecord> all=new LinkedHashMap<>();
    var settings=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,3,0,false);
    var main=Hotfix429ModelValidation.road(new V(0,108,0),new V(0,108,700),settings);
    var one=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,1,0,false);
    var sourceA=Hotfix429ModelValidation.road(new V(-300,100,100),new V(-220,100,100),one);
    var sourceB=Hotfix429ModelValidation.road(new V(-300,100,260),new V(-220,100,260),one);
    for(var r:List.of(main,sourceA,sourceB))all.put(r.id(),r);
    var a=Hotfix429ModelValidation.point(all,sourceA,40,0);var b=Hotfix429ModelValidation.point(all,main,300,0);
    var c=Hotfix429ModelValidation.point(all,sourceB,40,0);var d=Hotfix429ModelValidation.point(all,all.get(main.id()),430,1);
    var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
    var data=new RoadData();all.values().forEach(r->data.index.put(new RoadIndex.Built(r)));
    var first=LaneRamps.generate(null,all,new UUID(436,10),new UUID(0,1),new LanePoints.Link(a,b,options,null));Live436ModelValidation.apply(data,first,null);
    var saved=data.index.roads.get(first.id()).record;var before=saved.header();
    all=LaneTopology.records(data);
    var second=LaneRamps.generate(null,all,new UUID(436,11),new UUID(0,1),new LanePoints.Link(c,d,options,null));all.put(second.id(),second);
    var scope=LaneTopology.editScope(data,all,List.of(new RoadIndex.Built(second)),Set.of());
    check(scope.contains(main.id())&&!scope.contains(first.id()),"new sibling floods through shared host into existing ramp");
    Live436ModelValidation.apply(data,second,null);
    var after=data.index.roads.get(first.id()).record;
    check(after.alignment().equals(saved.alignment()),"adding sibling regenerates saved alignment");
    check(after.header().equals(before),"adding noncontact sibling changes saved ramp metadata");
    Live436ModelValidation.apply(data,null,second.id());
    check(data.index.roads.get(first.id()).record.header().equals(before),"deleting sibling changes old ramp");
    all=LaneTopology.records(data);var changed=all.get(main.id()).settings(main.settings().options(main.settings().options().outerRail(RoadProfile.OuterRail.OFF)));all.put(main.id(),changed);
    scope=LaneTopology.editScope(data,all,List.of(new RoadIndex.Built(changed)),Set.of(main.id()));
    check(scope.contains(first.id()),"real host edit lost dependent validation");
    System.out.println("CLOSE437 PASS: add two ramps sharing host, preserve first, delete second; actual topology planner, no world writes");
  }

 public static void main(String[] args){expansion();derivedScope();modeSwitch();closeSiblings();System.out.println("Live437ModelValidation: "+checks+" checks PASS; production planner/codec, world adapters");}
}
