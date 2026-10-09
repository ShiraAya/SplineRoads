package com.sora.splineroads.core;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
/** Solver coordinates are motor-lane axes. Convert them to pavement centers and fit both mouths. */
public final class LaneRampAlignment {
  /** .75 m on either side seats both ordinary and highway rail assemblies.
   * The persisted motor port distinguishes refreshed connectors from old saves. */
  public static Settings usableWidth(Settings settings,double drive){
    drive=Math.max(4,drive);var o=settings.options();
    var port=new RoadTransitions.Port(-drive/2,drive/2,0,List.of(),0,0);
    return new Settings(settings.mode(),settings.style(),drive+1.5,settings.thickness(),settings.tension(),settings.arcDegrees(),
        o.ends().start()==null?drive+1.5:settings.startWidth(),o.ends().end()==null?drive+1.5:settings.endWidth(),
        settings.structure(),settings.taperVersion(),settings.rampTurn(),o.ends(o.ends().port(port)));
  }
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
  public record Mouth(double lane,double low,double high,boolean auxiliary){
    public Mouth(double lane,double low,double high){this(lane,low,high,false);}
    public static Mouth extra(double lane){return new Mouth(lane,0,0,true);}
    boolean internal(){return !auxiliary&&low<1e-6&&high<1e-6;}
  }
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
    var clean=path.settings().options(path.settings().options().ends(RoadTransitions.Ends.NONE.port(path.settings().options().ends().port())));
    var a=section(clean,from);var b=section(clean,to);
    var settings=RoadTransitions.ends(clean,a,b);Mesh fitted=path;
    double[] sourceFree=outsideProgress(path,from,true),targetFree=outsideProgress(path,to,false);
    double shoulder=Math.max(0,(settings.width()-RoadProfile.layout(settings,settings.width()).laneWidth())/2);
    // Widening one shoulder changes physical arc length. Evaluate profile weights
    // on that final distance too, so serialization/reload cannot shift the motor axis.
    for(int pass=0;pass<12;pass++){
      double span=RoadTransitions.span(settings,fitted.length());var out=new ArrayList<Sample>();
      for(int i=0;i<path.samples().size();i++){
        var p=path.samples().get(i);double distance=fitted.samples().get(i).distance();
        double wa=1-Settings.smooth(Math.min(1,distance/span)),wb=1-Settings.smooth(Math.min(1,(fitted.length()-distance)/span));
        double shift=((from.high()-from.low())*wa+(to.high()-to.low())*wb)/2;
        // Grow a new rail shoulder OUTWARD while the axis still follows an outer
        // host slot. Symmetric widening of a straight lead invaded its unchanged
        // inward neighbor immediately, making every fixed-start route impossible.
        double bias=Math.signum(from.high()-from.low())*(1-sourceFree[i])+Math.signum(to.high()-to.low())*(1-targetFree[i]);
        shift+=shoulder*(1-wa)*(1-wb)*Math.max(-1,Math.min(1,bias));
        double width=settings.width()+(a.width()-settings.width())*wa+(b.width()-settings.width())*wb;
        // An interior fixed mouth has no spare shoulder in either adjacent lane.
        // Keep its authored slot width through the straight lead; widening starts
        // with the separated turn, whose whole ribbon still undergoes clearance.
        if(from.internal())width=Math.min(width,from.lane()+2*shoulder*sourceFree[i]);
        if(to.internal())width=Math.min(width,to.lane()+2*shoulder*targetFree[i]);
        // EXTRA already has an exact host-following pavement taper. A second,
        // shorter width interpolation must not pull that shoulder away from it.
        if(from.auxiliary()&&wa>0||to.auxiliary()&&wb>0)width=Math.max(width,p.halfWidth()*2);
        out.add(new Sample(p.center().add(p.left().mul(shift)),p.left(),p.distance(),width/2));
      }
      var next=RoadRibbon.mesh(out,settings);double movement=0;
      for(int i=0;i<out.size();i++)movement=Math.max(movement,next.samples().get(i).center().distance(fitted.samples().get(i).center()));
      fitted=next;if(movement<1e-9)break;
    }
    return fitted;
  }
  private static double[] outsideProgress(Mesh path,Mouth mouth,boolean first){
    int n=path.samples().size();var end=first?path.first():path.last();double side=Math.signum(mouth.high()-mouth.low()),progress=0;
    var result=new double[n];
    for(int i=first?0:n-1;i>=0&&i<n;i+=first?1:-1){
      var delta=path.samples().get(i).center().sub(end.center());
      double lateral=delta.dot(end.left());
      progress=Math.max(progress,side==0?Math.max(Math.abs(lateral)-.5,Math.abs(delta.y())-5.5):Math.abs(lateral));
      result[i]=mouth.auxiliary()?1:Settings.smooth(Math.max(0,Math.min(1,progress/Math.max(1,path.settings().width()))));
    }return result;
  }
  /** Old saved paths can have correct lane axes and still miss an outside shoulder. */
  public static boolean matches(Mesh mesh,Mouth from,Mouth to){
    for(boolean first:new boolean[]{true,false}){
      var s=first?mesh.first():mesh.last();var mouth=first?from:to;
      var lane=LanePoints.lane(mesh,first?0:mesh.length(),0);
      V center=lane.position().add(s.left().mul((mouth.high()-mouth.low())/2));
      if(Math.abs(lane.width()-mouth.lane())>1e-6||center.distance(s.center())>1e-6
          ||Math.abs(s.halfWidth()*2-mouth.lane()-mouth.low()-mouth.high())>1e-6)return false;
    }return true;
  }
  public static Mesh refit(Mesh mesh,Mouth from,Mouth to){
    var axis=new ArrayList<Sample>();
    for(var s:mesh.samples())axis.add(new Sample(LanePoints.lane(mesh,s.distance(),0).position(),s.left(),s.distance(),s.halfWidth()));
    return fit(RoadRibbon.mesh(axis,mesh.settings()),from,to);
  }
  public static V axis(Mesh mesh,boolean first){return LanePoints.lane(mesh,first?0:mesh.length(),0).position();}
  private LaneRampAlignment(){}
}
