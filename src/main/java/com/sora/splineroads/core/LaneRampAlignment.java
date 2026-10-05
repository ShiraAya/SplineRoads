package com.sora.splineroads.core;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
/** Solver coordinates are motor-lane axes. Convert them to pavement centers and fit both mouths. */
public final class LaneRampAlignment {
  public static Mesh fit(Mesh path,double startLane,double endLane,double transition){
    var profile=RoadProfile.layout(path.settings(),path.settings().width());
    double lane=profile.laneWidth(),margin=path.settings().width()-lane;
    double span=Math.max(.01,Math.min(Math.max(16,transition),path.length()/2));
    var out=new ArrayList<Sample>();
    for(var s:path.samples()){
      double a=1-Settings.smooth(Math.min(1,s.distance()/span)),b=1-Settings.smooth(Math.min(1,(path.length()-s.distance())/span));
      double width=lane+(startLane-lane)*a+(endLane-lane)*b+margin;
      V center=s.center().sub(s.left().mul(profile.motorCenter()));
      out.add(new Sample(center,s.left(),s.distance(),width/2));
    }
    return RoadRibbon.mesh(out,path.settings());
  }
  public static V axis(Mesh mesh,boolean first){return LanePoints.lane(mesh,first?0:mesh.length(),0).position();}
  private LaneRampAlignment(){}
}
