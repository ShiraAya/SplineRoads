package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Reproducible TEMPORARY -> EXTRA / 24 / 32 / AUTO / FLEXIBLE fixture.
 * This is not the user's unavailable saved world. Time only route generation. */
public final class Planner443SceneBenchmark {
  public static void main(String[] args){
    var s=Hotfix429ModelValidation.settings(RoadProfile.Type.ORDINARY,2,3,false).structure(Structure.BRIDGE);
    var a=Hotfix429ModelValidation.road(new V(0,20,0),new V(0,20,500),s);
    var b=Hotfix429ModelValidation.road(new V(90,28,200),new V(90,28,700),s);
    var all=new LinkedHashMap<UUID,RoadRecord>();all.put(a.id(),a);all.put(b.id(),b);
    int slot=LaneSections.live(a.mesh(),80).lanes().stream().filter(l->l.sign()==1&&LaneSections.edge(a.mesh(),80,l.index())).findFirst().orElseThrow().index();
    var from=Hotfix429ModelValidation.point(all,a,80,slot);var to=Hotfix429ModelValidation.point(all,b,200,slot);
    var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.EXTRA,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.FLEXIBLE);
    var link=new LanePoints.Link(from,to,options,null);double[] times=new double[3];
    for(int i=0;i<3;i++){
      long start=System.nanoTime();var r=LaneRamps.generate(null,all,new UUID(443,100),a.owner(),link);times[i]=(System.nanoTime()-start)/1e6;
      LaneRampGrade.validate(r.mesh(),LaneRamps.gradeLimit(all,LaneTopology.metadata(r).link()));
      System.out.printf(Locale.ROOT,"SCENE443 run=%d ms=%.3f length=%.6f samples=%d geometry_hash=%d%n",i,times[i],r.mesh().length(),r.mesh().samples().size(),r.mesh().samples().hashCode());
    }
    Arrays.sort(times);System.out.printf(Locale.ROOT,"SCENE443 median_ms=%.3f%n",times[1]);
  }
}
