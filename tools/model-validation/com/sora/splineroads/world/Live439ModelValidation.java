package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
public final class Live439ModelValidation {
 static int checks;
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void mixed(){
  for(int direction:new int[]{1,-1}){
   var s=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,1,0,false);
   var source=Hotfix429ModelValidation.road(new V(0,direction>0?100:112,0),new V(0,direction>0?100:112,80),s);
   var target=Hotfix429ModelValidation.road(new V(0,direction>0?112:100,400),new V(0,direction>0?112:100,480),s);
   var low=Hotfix429ModelValidation.road(new V(-300,102,direction>0?190:310),new V(300,102,direction>0?190:310),s);
   var high=Hotfix429ModelValidation.road(new V(-300,120,direction>0?310:190),new V(300,120,direction>0?310:190),s);
   var all=new LinkedHashMap<UUID,RoadRecord>();for(var r:List.of(source,target,low,high))all.put(r.id(),r);
   var a=Hotfix429ModelValidation.point(all,source,40,0);var b=Hotfix429ModelValidation.point(all,target,40,0);
   var options=new LanePoints.Options(LanePoints.Path.DIRECT,LanePoints.Departure.BRANCH,LanePoints.Arrival.FLOW,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
   var ramp=LaneRamps.generate(null,all,UUID.randomUUID(),source.owner(),new LanePoints.Link(a,b,options,null));
   check(LaneRamps.monotone(ramp.mesh()),"mixed crossing unnecessarily reverses grade direction "+direction);
   for(var road:List.of(low,high))for(var contact:RoadClearance.contacts(ramp.mesh(),road.mesh()))check(!contact.blocked(),"monotone path bypassed clearance");
   var snapshot=RoadRecord.load(ramp.save());check(snapshot.alignment().equals(ramp.alignment()),"profile changed after save");
  }
 }
 public static void main(String[]args){mixed();System.out.println("Live439ModelValidation: "+checks+" checks PASS; ascending/descending mixed-road clearance and NBT adapter round trip");}
}
