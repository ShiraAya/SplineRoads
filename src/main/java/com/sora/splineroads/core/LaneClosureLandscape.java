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
  public static final double MIN_GREEN_LENGTH=12;
  public static List<Part> plan(Mesh mesh,Ground ground){
    if(mesh.settings().structure()==Structure.TUNNEL)return List.of();
    var raw=LaneSections.reference(mesh);var result=new ArrayList<Part>();
    for(var cut:closedIntervals(mesh)){
      int count=(int)Math.ceil(cut.end()-cut.begin());boolean[] clear=new boolean[count];
      // Find whole usable planting runs. Independently clipping soil and leaves
      // created exposed soil wedges and chopped fronts under rising decks.
      for(int i=0;i<count;i++){
        double a=cut.begin()+i,b=Math.min(cut.end(),a+1);
        clear[i]=!raised(mesh,cut.lane(),(a+b)/2,ground)&&bed(raw,cut.lane(),a,b,1,1).stream().noneMatch(ground::blocked);
      }
      for(int i=0;i<count;){if(!clear[i]){i++;continue;}int end=i+1;while(end<count&&clear[end])end++;
        double from=cut.begin()+i,to=Math.min(cut.end(),cut.begin()+end);
        if(to-from>=MIN_GREEN_LENGTH){
          var run=new ArrayList<Part>();
          for(int j=i;j<end;j++){
            double a=Math.max(from+.2,cut.begin()+j),b=Math.min(to-.2,cut.begin()+j+1);
            if(b<=a)continue;
            run.addAll(bed(raw,cut.lane(),a,b,taper(a,from,to),taper(b,from,to)));
          }
          for(double d:new double[]{from+.1,to-.1}){
            var at=RoadStructures.sample(raw,d);var lane=LanePoints.lane(raw,d,cut.lane());
            double width=(lane.width()-.12)*taper(d<from+1?from+.2:to-.2,from,to),depth=Math.max(.5,raw.settings().thickness()+.125);
            V center=lane.position().add(new V(0,-depth,0));double half=Math.max(.08,width/2);
            run.add(new Part(center.sub(at.left().mul(half)),center.add(at.left().mul(half)),.2,depth+.35,false,Material.CONCRETE));
          }
          if(run.stream().noneMatch(ground::blocked)){result.addAll(run);}
        }i=end;
      }
      // Seal the entire closed slot, including the corners beside tapered planting.
      // Shrinking the greenery must never shrink its underlying road foundation.
      for(int i=0;i<count;i++){
        double a=cut.begin()+i,b=Math.min(cut.end(),a+1);
        if(raised(mesh,cut.lane(),(a+b)/2,ground))continue;
        var la=LanePoints.lane(raw,a,cut.lane());var lb=LanePoints.lane(raw,b,cut.lane());
        var sa=RoadStructures.sample(raw,a);var sb=RoadStructures.sample(raw,b);double depth=Math.max(.5,raw.settings().thickness()+.125);
        var pad=new Part(la.position().add(new V(0,-depth,0)),lb.position().add(new V(0,-depth,0)),Math.max(la.width(),lb.width()),depth+.02,false,Material.CONCRETE)
            .frames(sa.left().mul(la.width()/2),sb.left().mul(lb.width()/2));
        addUnblocked(result,pad,ground,0);
      }
    }return List.copyOf(result);
  }
  private static double taper(double d,double from,double to){return .2+.8*Settings.smooth(Math.min(1,Math.min(d-from,to-d)/2));}
  private static List<Part> bed(Mesh raw,int slot,double a,double b,double wa,double wb){
    var la=LanePoints.lane(raw,a,slot);var lb=LanePoints.lane(raw,b,slot);
    return RoadStructures.planting(la.position(),lb.position(),RoadStructures.sample(raw,a).left(),RoadStructures.sample(raw,b).left(),
        (la.width()-.12)*wa,(lb.width()-.12)*wb,1,Math.max(.5,raw.settings().thickness()+.125));
  }
  private static boolean connectorAddition(Mesh mesh,LaneSections.Cut cut){
    return cut.lane()>=8&&mesh.settings().options().lanePoints().additions().stream().anyMatch(a->a.slot()==cut.lane()&&a.connection().equals(cut.connection()));
  }
  private record Interval(int lane,double begin,double end){}
  private static List<Interval> closedIntervals(Mesh mesh){
    var raw=LaneSections.reference(mesh);var intervals=new ArrayList<Interval>();
    for(var cut:mesh.settings().options().lanePoints().cuts())if(cut.temporary()&&cut.rectangular()&&!cut.underpass()&&!connectorAddition(mesh,cut)){
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
  /** Clip only the low unplanted foundation. Vegetation is accepted as a complete
   * run above; subdividing its individual layers would expose cut soil faces. */
  private static void addUnblocked(List<Part> out,Part part,Ground ground,int depth){
    if(!ground.blocked(part)){out.add(part);return;}
    if(depth>=4||part.a().sub(part.b()).horizontalLength()<.125)return;
    // Unframed end kerbs use Part.base()'s implicit lateral frame. Do not dereference null,
    // and preserve this same prism when subdivision makes the frames explicit.
    V side=part.pier()||part.b().sub(part.a()).horizontalLength()<1e-8?new V(part.width()/2,0,0):part.b().sub(part.a()).horizontalUnit().left().mul(part.width()/2);
    V first=part.frameA()==null?side:part.frameA(),last=part.frameB()==null?side:part.frameB();
    V mid=part.a().add(part.b()).mul(.5),frame=first.add(last).mul(.5);
    addUnblocked(out,new Part(part.a(),mid,part.width(),part.height(),part.pier(),part.material(),first,frame,part.model()),ground,depth+1);
    addUnblocked(out,new Part(mid,part.b(),part.width(),part.height(),part.pier(),part.material(),frame,last,part.model()),ground,depth+1);
  }
  private LaneClosureLandscape(){}
}
