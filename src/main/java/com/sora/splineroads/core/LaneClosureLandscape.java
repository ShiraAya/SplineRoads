package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Q2-16/17: replace a closed ground lane with actual planting solids, never a
 * drivable painted island. Elevated slots remain the rectangular LaneDeck void.
 * Classification uses ORIGINAL terrain at the selected lane, not absolute world Y
 * or a majority vote over the unrelated half of a wide road. */
public final class LaneClosureLandscape {
  public static boolean raised(Mesh mesh,int slot,double station,Ground ground){
    if(mesh.settings().structure()==Structure.BRIDGE)return true;
    if(mesh.settings().structure()==Structure.GROUND)return false;
    if(mesh.settings().structure()==Structure.TUNNEL)return true; // no tunnel vegetation
    var raw=LaneSections.reference(mesh);var center=LanePoints.lane(raw,station,slot);
    double threshold=Math.max(.5,raw.settings().thickness()+.125);int unsupported=0;
    for(double along:new double[]{-.65,0,.65}){
      var at=RoadStructures.sample(raw,Math.max(raw.first().distance(),Math.min(raw.last().distance(),station+along)));
      var lane=LanePoints.lane(raw,at.distance(),slot);
      for(double across:new double[]{-.35,0,.35}){
        V p=lane.position().add(at.left().mul(across*center.width()));
        double top=ground.top(p.x(),p.z(),p.y());
        if(!Double.isFinite(top)||p.y()-top>threshold)unsupported++;
      }
    }
    // A one-block construction spine or a missing center block cannot classify the
    // whole lane by itself. Unknown/water columns fail closed to a non-floating gap.
    return unsupported>=5;
  }
  public static List<Part> plan(Mesh mesh,Ground ground){
    if(mesh.settings().structure()==Structure.TUNNEL)return List.of();
    var raw=LaneSections.reference(mesh);var result=new ArrayList<Part>();
    // Multiple live links may reserve overlapping pieces of the same slot.
    // Materialize their union once, rather than stacking duplicate soil/kerbs/leaves.
    for(var cut:closedIntervals(mesh)){
      double start=cut.begin(),end=cut.end();
      for(double d=start;d<end-1e-7;d+=1){
        double next=Math.min(end,d+1),mid=(d+next)/2;
        if(raised(mesh,cut.lane(),mid,ground))continue;
        var a=RoadStructures.sample(raw,d);var b=RoadStructures.sample(raw,next);
        var la=LanePoints.lane(raw,d,cut.lane());var lb=LanePoints.lane(raw,next,cut.lane());
        var bed=RoadStructures.planting(la.position(),lb.position(),a.left(),b.left(),la.width()-.12,lb.width()-.12,1,
            Math.max(.5,raw.settings().thickness()+.125));
        // Each actual solid is checked separately. Foliage intersecting a ramp at
        // the mouth must not remove the nonintersecting subgrade foundation/curbs.
        // Once the rising deck clears the bed, the complete normal-road bed resumes.
        for(var part:bed)addUnblocked(result,part,ground,0);
      }
      // Close both exposed ends, after unioning reservations. Longitudinal kerbs
      // alone leave bare soil at a rectangular closure's front/back face.
      for(double d:new double[]{start+.1,end-.1})if(d>=start&&d<=end&&!raised(mesh,cut.lane(),d,ground)){
        var at=RoadStructures.sample(raw,d);var lane=LanePoints.lane(raw,d,cut.lane());
        double half=(Math.max(.08,lane.width()-.12-.3))/2+.2;
        double depth=Math.max(.5,raw.settings().thickness()+.125);
        V center=lane.position().add(new V(0,-depth,0));
        var cap=new Part(center.sub(at.left().mul(half)),center.add(at.left().mul(half)),.2,depth+.35,false,Material.CONCRETE);
        addUnblocked(result,cap,ground,0);
      }
    }
    return List.copyOf(result);
  }
  private record Interval(int lane,double begin,double end){}
  private static List<Interval> closedIntervals(Mesh mesh){
    var raw=LaneSections.reference(mesh);var intervals=new ArrayList<Interval>();
    for(var cut:mesh.settings().options().lanePoints().cuts())if(cut.temporary()&&cut.rectangular()){
      double a=Math.max(raw.first().distance(),Math.min(cut.begin(),cut.end()));
      double b=Math.min(raw.last().distance(),Math.max(cut.begin(),cut.end()));
      if(b>a+1e-7)intervals.add(new Interval(cut.lane(),a,b));
    }
    intervals.sort(Comparator.comparingInt(Interval::lane).thenComparingDouble(Interval::begin));
    var merged=new ArrayList<Interval>();
    for(var next:intervals){
      if(!merged.isEmpty()){
        var last=merged.get(merged.size()-1);
        if(last.lane()==next.lane()&&next.begin()<=last.end()+1e-7){
          merged.set(merged.size()-1,new Interval(last.lane(),last.begin(),Math.max(last.end(),next.end())));continue;
        }
      }
      merged.add(next);
    }
    return merged;
  }
  /** Clip an obstructed component locally; never discard its unobstructed soil or
   * kerbs because a leaf layer meets the rising ramp. A bounded subdivision avoids
   * full 1-metre gaps at a partial crossing and retains the same framed solid/UVs. */
  private static void addUnblocked(List<Part> out,Part part,Ground ground,int depth){
    if(!ground.blocked(part)){out.add(part);return;}
    if(depth>=4||part.a().sub(part.b()).horizontalLength()<.125)return;
    V mid=part.a().add(part.b()).mul(.5),frame=part.frameA().add(part.frameB()).mul(.5);
    addUnblocked(out,new Part(part.a(),mid,part.width(),part.height(),false,part.material()).frames(part.frameA(),frame),ground,depth+1);
    addUnblocked(out,new Part(mid,part.b(),part.width(),part.height(),false,part.material()).frames(frame,part.frameB()),ground,depth+1);
  }
  private LaneClosureLandscape(){}
}
