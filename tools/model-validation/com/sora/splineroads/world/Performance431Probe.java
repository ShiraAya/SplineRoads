package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
/** Identical old/new input. Actual planner with explicit model adapters, not a
 * user world, GUI or Forge server. Timing is diagnostic, never a success timeout. */
public final class Performance431Probe {
  public static void main(String[] args){
    int reps=args.length==0?6:Integer.parseInt(args[0]);
    for(int extra:new int[]{0,40}){
      var target=Arrival417ModelValidation.road(new V(0,100,0),new V(0,100,600),Style.H6_RAIL,false);
      var source=Arrival417ModelValidation.road(new V(-250,88,240),new V(250,88,240),Style.H6_RAIL,false);
      var all=new LinkedHashMap<UUID,RoadRecord>();all.put(source.id(),source);all.put(target.id(),target);
      var a=Arrival417ModelValidation.point(all,source,80,3);var b=Arrival417ModelValidation.point(all,target,420,5);
      for(int j=0;j<extra;j++){var far=Arrival417ModelValidation.road(new V(1500+j*60,100,0),new V(1500+j*60,100,1500),Style.H6_RAIL,false);all.put(far.id(),far);}
      var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.FLEXIBLE);
      for(int i=0;i<reps;i++){
        long start=System.nanoTime();var road=LaneRamps.generate(null,all,UUID.randomUUID(),source.owner(),new LanePoints.Link(a,b,options,null));
        var m=road.mesh();long geometry=1;
        for(var s:m.samples())for(double v:new double[]{s.center().x(),s.center().y(),s.center().z(),s.halfWidth()})geometry=31*geometry+Math.round(v*1e7);
        System.out.printf(Locale.ROOT,"PERF431 extra=%d run=%d plan_ms=%.3f length=%.9f samples=%d signature=%d%n",extra,i,(System.nanoTime()-start)/1e6,m.length(),m.samples().size(),geometry);
      }
    }
  }
}
