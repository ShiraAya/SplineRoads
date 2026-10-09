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
 public static void main(String[]args){supportCut();System.out.println("Live442Validation: "+checks+" checks PASS");}
}
