package com.sora.splineroads;
import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadGeometry.*;import java.util.*;
public final class Live442Validation {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void supportCut(){
  for(int slot:new int[]{0,1,2})for(var bridge:List.of(RoadInfrastructure.Bridge.STANDARD,RoadInfrastructure.Bridge.BEAM,RoadInfrastructure.Bridge.OVERPASS)){
   var structure=bridge==RoadInfrastructure.Bridge.STANDARD?Structure.AUTO:Structure.BRIDGE;
   var base=Live435Validation.road(Style.O3_ONE,structure);var settings=base.settings().options(base.settings().options().infrastructure(base.settings().options().infrastructure().bridge(bridge)));var raw=RoadRibbon.mesh(base.samples(),settings);var host=Live435Validation.cut(raw,slot,40,160,false);
   var parts=structure==Structure.AUTO?RoadStructures.supports(host,Live435Validation.ground(0),RoadFurniture.Phase.DEFAULT):RoadInfrastructure.plan(host,Live435Validation.ground(0)).stream().filter(p->p.pier()||Math.abs(p.a().z()-p.b().z())<1e-6).toList();
   check(parts.stream().anyMatch(p->p.pier()),"cut erased every support");
   for(var p:parts)for(var v:p.base()){
    var q=RoadQueries.horizontal(host,v);
    if(v.y()<=q.sample().center().y()+.01&&q.sample().distance()>42&&q.sample().distance()<158)
     check(LaneDeck.present(host,q.sample(),v.sub(q.sample().center()).dot(q.sample().left()),.001),"support projects into removed lane slot "+slot+": "+p);
   }
  }
 }
 static void permanentLaneRail(){
  for(var style:List.of(Style.O3_ONE,Style.O4_RAIL,Style.H4_RAIL))for(boolean left:new boolean[]{false,true}){
   var initial=Live435Validation.road(style,Structure.BRIDGE);var raw=RoadRibbon.mesh(initial.samples(),initial.settings().options(initial.settings().options().traffic(left)));
   for(var closed:LaneSections.live(raw,100).lanes())if(LaneSections.edge(raw,100,closed.index())){
    var near=LaneSections.live(raw,100).lanes().stream().filter(l->l.index()!=closed.index()&&l.sign()==closed.sign()).min(Comparator.comparingDouble(l->l.position().distance(closed.position()))).orElseThrow();
    var cut=new LaneSections.Cut(UUID.randomUUID(),closed.index(),closed.sign(),closed.sign()>0?40:160,closed.sign()>0?250:-50,32,null,false,false,true);
    var host=LaneSections.apply(RoadRibbon.mesh(raw.samples(),raw.settings().options(raw.settings().options().lanePoints(LanePoints.Data.EMPTY.cuts(List.of(cut))))));
    var parts=RoadStructures.plan(host,Live435Validation.ground(0));var points=new ArrayList<Sample>();
    for(double d=50;d<=150;d++){var lane=LanePoints.lane(raw,d,near.index());points.add(new Sample(lane.position(),RoadStructures.sample(raw,d).left(),d-50,lane.width()/2-.001));}
    var travel=RoadRibbon.mesh(points,new Settings(Mode.STRAIGHT,Style.C1_RAMP,4,1,.35,90));
    check(parts.stream().filter(p->!p.pier()&&Math.abs(p.a().y()-20)<1e-6&&p.height()<2).noneMatch(p->RoadClearance.structureInvades(p,travel,4.25)),"permanent DETACH rail narrows adjacent live lane: "+style+" left="+left+" slot="+closed.index());
   }
  }
 }
 public static void main(String[]args){supportCut();permanentLaneRail();System.out.println("Live442Validation: "+checks+" checks PASS");}
}
