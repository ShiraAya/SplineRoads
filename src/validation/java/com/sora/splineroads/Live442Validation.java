package com.sora.splineroads;
import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadGeometry.*;import java.util.*;
public final class Live442Validation {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void supportCut(){
  for(int slot:new int[]{0,1,2}){
   var raw=Live435Validation.road(Style.O3_ONE,Structure.AUTO);var host=Live435Validation.cut(raw,slot,40,160,false);
   var parts=RoadStructures.supports(host,Live435Validation.ground(0),RoadFurniture.Phase.DEFAULT);
   check(parts.stream().anyMatch(p->p.pier()),"cut erased every support");
   for(var p:parts)for(var v:p.base()){
    var q=RoadQueries.horizontal(host,v);
    if(q.sample().distance()>42&&q.sample().distance()<158)
     check(LaneDeck.present(host,q.sample(),v.sub(q.sample().center()).dot(q.sample().left()),.001),"support projects into removed lane slot "+slot+": "+p);
   }
  }
 }
 public static void main(String[]args){supportCut();System.out.println("Live442Validation: "+checks+" checks PASS");}
}
