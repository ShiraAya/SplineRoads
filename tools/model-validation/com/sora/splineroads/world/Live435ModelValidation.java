package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
public final class Live435ModelValidation {
  static int checks;
  static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
  static void endpoints(){
    for(boolean arrival:new boolean[]{false,true})for(boolean terminal:new boolean[]{false,true}){
      var all=new LinkedHashMap<UUID,RoadRecord>();
      var road=Hotfix429ModelValidation.road(new V(0,100,0),new V(0,100,200),Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,3,0,false));all.put(road.id(),road);
      double station=arrival?road.rawMesh().length()-(terminal?.5:20):(terminal?.5:20);
      var ref=Hotfix429ModelValidation.point(all,road,station,1);
      var events=new LinkedHashMap<UUID,List<LaneSections.Event>>();
      LaneRoadChain.of(all,ref).reserve(events,new UUID(435,1),null,List.of(),arrival,0,32,true);
      var event=events.get(road.id()).get(0);
      double expected=terminal?(arrival?road.rawMesh().length():0):station;
      check(Math.abs(event.station()-expected)<1e-7,"half-block terminal not closed, or interior point incorrectly expanded");
    }
  }
  static void overFirst(double rise,boolean ceiling) throws Exception {
    var settings=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,1,0,false);
    var source=Hotfix429ModelValidation.road(new V(0,100,0),new V(0,100,80),settings);
    var target=Hotfix429ModelValidation.road(new V(0,100+rise,300),new V(0,100+rise,380),settings);
    var obstacle=Hotfix429ModelValidation.road(new V(-500,100+rise,190),new V(500,100+rise,190),settings);
    var all=new LinkedHashMap<UUID,RoadRecord>();for(var r:List.of(source,target,obstacle))all.put(r.id(),r);
    var a=Hotfix429ModelValidation.point(all,source,40,0);var b=Hotfix429ModelValidation.point(all,target,40,0);
    var options=new LanePoints.Options(LanePoints.Path.DIRECT,LanePoints.Departure.BRANCH,LanePoints.Arrival.FLOW,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
    var link=new LanePoints.Link(a,b,options,null);RoadRecord built;
    if(ceiling){
      var constructor=LaneRamps.PreviewWork.class.getDeclaredConstructors()[0];constructor.setAccessible(true);
      var work=(LaneRamps.PreviewWork)constructor.newInstance(all,Map.of(),new UUID(435,4),new UUID(435,5),link,null,0L,0.0,104.5+rise);
      built=work.compute().road();
    }else built=LaneRamps.generate(null,all,new UUID(435,4),new UUID(435,5),link);
    var contacts=RoadClearance.contacts(built.mesh(),obstacle.mesh());check(!contacts.isEmpty(),"fixture misses obstacle");
    for(var c:contacts){check(!c.blocked(),"fallback bypassed clearance");check((c.ours().y()>c.other().y())!=ceiling,"AUTO did not prefer over / fall back under blocked ceiling");}
    check(LaneTopology.metadata(built).link().options().elevation()==LanePoints.Elevation.AUTO,"automatic policy changed saved user choice");
    System.out.println("AUTO435 rise="+rise+" ceiling="+ceiling+" PASS");
  }
  public static void main(String[] args) throws Exception {endpoints();for(double rise:new double[]{0,8}){overFirst(rise,false);overFirst(rise,true);}System.out.println("Live435ModelValidation: "+checks+" checks PASS; production planner and endpoint reservations, NO world writes.");}
}
