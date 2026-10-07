package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

public final class AutomaticTurns434Validation {
 static int checks;
 static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static V transform(V p,double angle,V offset){return new V(p.x()*Math.cos(angle)-p.z()*Math.sin(angle),p.y(),p.x()*Math.sin(angle)+p.z()*Math.cos(angle)).add(offset);}
 public static void main(String[] args){
  for(boolean mirror:new boolean[]{false,true})for(double angle:new double[]{0,.73,2.2}){
   V offset=new V(123456,0,-654321),zero=new V(0,0,0);double side=mirror?-1:1;
   var a=new LaneRampPaths.Port(transform(new V(-10.5*side,64,-250),angle,offset),transform(new V(0,0,1),angle,zero),transform(new V(-side,0,0),angle,zero),4,0);
   var b=new LaneRampPaths.Port(transform(new V(250*side,72,-10.5),angle,offset),transform(new V(-side,0,0),angle,zero),transform(new V(0,0,-1),angle,zero),4,0);
   var settings=new Settings(Mode.CURVE,Style.C1_RAMP,4,1,.35,90);
   var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
   var candidates=LaneRampPaths.automaticTurns(a,b,settings,options,.2);ok(!candidates.isEmpty(),"direction-constrained route behind B tangent missing");
   for(var candidate:candidates){var m=candidate.mesh();ok(m.first().center().distance(a.position())<1e-6&&m.last().center().distance(b.position())<1e-6,"moved selected ports");ok(RoadRibbon.start(m).direction().dot(a.direction())>.999999&&RoadRibbon.end(m).direction().dot(b.direction())>.999999,"reversed lane heading");ok(RoadRibbon.minRadius(m)>23,"radius collapsed");LaneRampGrade.validate(m,.2);RoadRibbon.checkSelfIntersections(m,4);LaneRampPaths.checkVolume(m);}
   boolean rejected=false;try{var far=new LaneRampPaths.Port(b.position().add(new V(4000,0,0)),b.direction(),b.outside(),4,0);LaneRampPaths.automaticTurns(a,far,settings,options,.2);}catch(IllegalArgumentException expected){rejected=true;}ok(rejected,"AUTO bypassed maximum distance");
   rejected=false;try(var budget=RoadPlanningBudget.open("cancel regression",60,()->true)){LaneRampPaths.automaticTurns(a,b,settings,options,.2);}catch(RoadPlanningBudget.Aborted expected){rejected=true;}ok(rejected,"cancellation swallowed as bad geometry");
  }
  System.out.println("AutomaticTurns434Validation: "+checks+" checks PASS");
 }
}
