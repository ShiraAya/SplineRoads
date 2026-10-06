package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadJunction.Paint;
import java.util.*;

/** Guidance for lane-point connectors only. Ordinary/interchange paint is unchanged. */
public final class LaneRampPaint {
  /** General arrows are absent for this road kind. Only EXTRA arrivals need a merge
   * arrow, located on their actual additional lane, before its narrowing taper.
   * The generic hideArrows option describes general arrows, not this required guide. */
  public static List<Paint> arrows(Mesh ramp,List<Mesh> neighbors) {
    if(!ramp.settings().style().connectorRamp())return List.of();
    var link=ramp.settings().options().lanePoints().link();
    if(link==null||!link.options().targetExtra()||link.to().road()==null)return List.of();
    Mesh target=null;LanePoints.Point point=null;
    for(var other:neighbors)for(var p:other.settings().options().lanePoints().points())
      if(p.id().equals(link.to().point())){target=other;point=p;break;}
    if(target==null)return List.of(); // Never guess a nearby parallel/opposing host.
    double length=LaneRampApproach.length(link.options().transition());
    double taper=LaneRampApproach.taper(link.options().transition());
    // Approach distances are horizontal sampled lengths, not just authored station;
    // search a bounded upstream window and validate every actual vertex.
    double min=Math.max(4,ramp.length()-length*1.5),max=ramp.length()-4;
    double preferred=Math.max(min,ramp.length()-taper-8);
    var candidates=new LinkedHashSet<Double>();
    candidates.add(preferred);
    for(double d=preferred-1;d>=min;d-=1)candidates.add(d);
    for(double d=preferred+1;d<=max;d+=1)candidates.add(d);
    for(double d:candidates){
      var at=RoadStructures.sample(ramp,d);var q=RoadQueries.horizontal(target,at.center());
      var lane=LanePoints.lane(target,q.sample().distance(),point.lane());
      V forward=at.left().left().mul(-1);
      if(forward.dot(lane.direction())<.94||Math.abs(at.center().y()-lane.position().y())>.15)continue;
      double lateral=lane.position().sub(at.center()).dot(at.left());
      if(Math.abs(lateral)<at.halfWidth()+.5)continue;
      double side=Math.signum(lateral);var faces=guide(ramp,d,side);
      boolean fits=!faces.isEmpty();
      for(var face:faces)for(V p:face.points()){
        var here=RoadQueries.horizontal(ramp,p);var layout=RoadProfile.layout(ramp,here.sample());
        if(here.sample().distance()<1||here.sample().distance()>ramp.length()-1
            ||here.lateral()<layout.motorMin()+.18||here.lateral()>layout.motorMax()-.18
            ||!RoadQueries.contains(ramp,p,-.1,.08)||RoadQueries.contains(target,p,.1,.15))fits=false;
      }
      if(fits)return faces; // Keep shaft, bend and head as an indivisible silhouette.
    }
    return List.of();
  }

  private static List<Paint> guide(Mesh mesh,double station,double side){
    // Local (along, lateral): 4.4-block long bent merge arrow. Map every vertex to
    // sampled road height/frame, so both slopes and curved auxiliary lanes stay coplanar.
    double[][][] polygon={
      {{-2.2,-side*.40-.11},{-2.2,-side*.40+.11},{0,-side*.40+.11},{0,-side*.40-.11}},
      {{0,-side*.40-.11},{0,-side*.40+.11},{1.25,side*.32+.11},{1.25,side*.32-.11}},
      {{2.2,side*.88},{.95,side*.32-.48},{.95,side*.32+.48}}
    };
    var out=new ArrayList<Paint>();
    for(var shape:polygon){var vertices=new ArrayList<V>();for(var p:shape){
      var at=RoadStructures.sample(mesh,station+p[0]);var profile=RoadProfile.layout(mesh,at);
      vertices.add(at.at(profile.motorCenter()+p[1],0));
    }out.add(new Paint(List.copyOf(vertices),0xEDEEE2));}
    return List.copyOf(out);
  }
  private LaneRampPaint(){}
}
