package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
public final class Live440Validation {
 static int checks;static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static void guides(){
  var raw=Live435Validation.road(Style.O3_ONE,Structure.GROUND);
  var p=LanePoints.point(new UUID(440,1),LanePoints.Origin.MANUAL,raw,110,2);
  var host=RoadRibbon.mesh(raw.samples(),raw.settings().options(raw.settings().options().hideArrows(true).lanePoints(LanePoints.Data.EMPTY.points(List.of(p)))));
  double edge=host.first().halfWidth();
  for(var arrival:List.of(LanePoints.Arrival.MERGE,LanePoints.Arrival.ADD,LanePoints.Arrival.EXTRA)){
   var opt=new LanePoints.Options(LanePoints.Path.DIRECT,LanePoints.Departure.TEMPORARY,arrival,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
   var link=new LanePoints.Link(LanePoints.Ref.lane(new UUID(440,2),new UUID(440,3)),LanePoints.Ref.lane(new UUID(440,4),p.id()),opt,null).withProtectedMerge().withRectangularClosure();
   var strip=Live439Validation.strip(40,100,20,0,edge);
   var ramp=RoadRibbon.mesh(strip.samples(),strip.settings().options(strip.settings().options().lanePoints(LanePoints.Data.EMPTY.link(link))));
   var paint=RoadJunction.markings(host,List.of(),List.of(ramp),true);
   if(arrival==LanePoints.Arrival.EXTRA)check(!paint.isEmpty(),"auxiliary merge guide disappeared");
   else check(paint.isEmpty(),"full-lane arrival acquired a false dashed edge guide: "+arrival);
  }
 }
 static void planting(){
  var raw=Live435Validation.road(Style.O3_ONE,Structure.GROUND);var host=Live435Validation.cut(raw,1,40,180,false);
  Ground yes=new Ground(){public double top(double x,double z,double y){return 19;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
  check(LaneClosureLandscape.plan(host,yes).stream().anyMatch(p->p.material()==Material.GREEN),"ground closure fixture has no planting");
  Ground no=new Ground(){public double top(double x,double z,double y){return 19;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}public boolean closedLanePlanting(UUID c){return false;}};
  check(LaneClosureLandscape.plan(host,no).isEmpty(),"buried ramp got a planter or concrete lid");
 }
 public static void main(String[]args){guides();planting();System.out.println("Live440Validation: "+checks+" checks PASS; full-lane vs auxiliary guides, buried reservation vegetation and lid");}
}
