package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
public final class Live448ModelValidation {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void bikeMouths(){
  for(boolean traffic:new boolean[]{false,true})for(int sign:new int[]{-1,1}){
   var settings=Live447ModelValidation.sourceSettings().options(Live447ModelValidation.sourceSettings().options().traffic(traffic));
   var road=Hotfix429ModelValidation.road(new V(0,100,0),new V(0,100,300),settings);var m=road.mesh();
   int slot=-1;double outside=-1;for(int i=0;i<8;i++){var lane=LanePoints.lane(m,150,i);if(lane.sign()==sign&&Math.abs(lane.position().x())>outside){slot=i;outside=Math.abs(lane.position().x());}}
   var selected=LanePoints.lane(m,150,slot);var mouth=LaneRampAlignment.mouth(m,selected);
   check(mouth.lane()+mouth.low()+mouth.high()>=5.5-1e-8,"cycle verge incorrectly narrows the selected outer motor mouth");
   for(boolean source:new boolean[]{true,false}){
    var run=LaneRampApproach.build(m,slot,150,source,5.5,32);var free=source?run.get(run.size()-1):run.get(0);var q=RoadQueries.horizontal(m,free.center());int side=q.lateral()<0?-1:1;
    double inner=side*q.lateral()-free.halfWidth(),motor=side*RoadProfile.layout(m,q.sample()).outer(side);
    check(Math.abs(inner-motor-.02)<1e-7,"auxiliary departs beyond the cycle lane instead of the motor edge");
    check(inner<q.sample().halfWidth()-2,"cycle lane still counted as a motor-lane offset");
   }
  }
 }
 static void boundary(){
  for(int sign:new int[]{-1,1}){
   var road=Hotfix429ModelValidation.road(new V(0,20,0),new V(0,20,500),new Settings(Mode.STRAIGHT,Style.O6_GREEN,28,1,.35,90));
   var id=new UUID(448,sign+2);var add=new LaneAdditions.Addition(id,8,sign,250,32);
   var data=LanePoints.Data.EMPTY.additions(List.of(add)).cuts(List.of(new LaneSections.Cut(id,8,sign,250-sign*32,250,32,null,true,true,true)));
   var point=LanePoints.point(new UUID(448,20+sign),LanePoints.Origin.MANUAL,road.mesh(),150,0);data=data.points(List.of(point));road=road.withLanePoints(data);var m=road.mesh();
   var geometry=RoadSurface.build(m,List.of(),List.of());
   for(double t=.125;t<32;t+=.25){double d=250+sign*(t-32);var at=RoadStructures.sample(m,d);var raw=LaneSections.reference(m);var lane=LaneAdditions.lane(raw,d,8);int side=LaneAdditions.side(RoadProfile.layout(raw,RoadStructures.sample(raw,d)),sign);
    var seam=at.at(lane.position().sub(at.center()).dot(at.left())-side*lane.width()*add.fraction(d)/2,0);
    check(geometry.markings().stream().anyMatch(f->JunctionPaint.inside(f.points(),seam)),"ADD edge missing near the narrow taper tip at "+t);
   }
   for(double d=60;d<=430;d+=2){var at=RoadStructures.sample(m,d);var layout=RoadProfile.layout(m,at);for(int side:new int[]{-1,1})
    check(Math.abs(LaneDeck.edge(m,at,side)-layout.outer(side)-side*.14)<1e-8,"unused lane point/ADD widened the ordinary shoulder");
   }
  }
 }
 public static void main(String[]args){bikeMouths();boundary();System.out.println("Live448ModelValidation: "+checks+" checks PASS; motor-edge approaches, shoulder width and taper-tip continuity");}
}
