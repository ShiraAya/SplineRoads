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
  /** A port retains its motor axis while the pavement also meets an outside shoulder. */
  public record Mouth(double lane,double low,double high){}
  public static Mouth mouth(Mesh host,LanePoints.Lane lane){
    var at=RoadStructures.sample(host,lane.station());var layout=RoadProfile.layout(host,at);
    double center=lane.position().sub(at.center()).dot(at.left());
    double low=Math.abs(center-lane.width()/2-layout.motorMin())<.01&&layout.cycleWidth()<.01?Math.max(0,at.halfWidth()+layout.motorMin()):0;
    double high=Math.abs(center+lane.width()/2-layout.motorMax())<.01&&layout.cycleWidth()<.01?Math.max(0,at.halfWidth()-layout.motorMax()):0;
    // Convert the host construction frame into this lane's driving frame.
    return lane.direction().dot(at.left().left().mul(-1))>0?new Mouth(lane.width(),low,high):new Mouth(lane.width(),high,low);
  }
  private static RoadTransitions.Section section(Settings settings,Mouth mouth){
    double shift=(mouth.high()-mouth.low())/2,width=mouth.lane()+mouth.low()+mouth.high();
    return RoadTransitions.Section.of(new Settings(settings.mode(),settings.style(),width,settings.thickness(),settings.tension(),settings.arcDegrees()).options(settings.options())).port(new RoadTransitions.Port(-mouth.lane()/2-shift,mouth.lane()/2-shift,0,List.of(),0,0));
  }
  public static Mesh fit(Mesh path,Mouth from,Mouth to){
    var clean=path.settings().options(path.settings().options().ends(RoadTransitions.Ends.NONE));
    var a=section(clean,from);var b=section(clean,to);
    var settings=RoadTransitions.ends(clean,a,b);double span=RoadTransitions.span(settings,path.length());
    var out=new ArrayList<Sample>();
    for(var p:path.samples()){
      double wa=1-Settings.smooth(Math.min(1,p.distance()/span)),wb=1-Settings.smooth(Math.min(1,(path.length()-p.distance())/span));
      double shift=((from.high()-from.low())*wa+(to.high()-to.low())*wb)/2;
      double width=settings.width()+(a.width()-settings.width())*wa+(b.width()-settings.width())*wb;
      out.add(new Sample(p.center().add(p.left().mul(shift)),p.left(),p.distance(),width/2));
    }
    return RoadRibbon.mesh(out,settings);
  }
  public static V axis(Mesh mesh,boolean first){return LanePoints.lane(mesh,first?0:mesh.length(),0).position();}
  private LaneRampAlignment(){}
}
