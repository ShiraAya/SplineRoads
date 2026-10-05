package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Direction-constrained lane connector. Coordinates are sampled into the real road ribbon. */
public final class LaneRampPaths {
  public record Port(V position,V direction,V outside,double extraWidth,double grade,List<Sample> approach){
    public Port(V position,V direction,V outside,double extraWidth,double grade){this(position,direction,outside,extraWidth,grade,List.of());}
    public Port {approach=List.copyOf(approach);}
  }
  public record Candidate(LanePoints.Path path,Mesh mesh){}
  private record Frame(V p,V d){}
  public static List<Candidate> candidates(Port a,Port b,Settings settings,LanePoints.Options options){
    if(options.sourceExtra()||options.targetExtra())return expanded(a,b,settings,options);
    List<Candidate> out=new ArrayList<>();var errors=new EnumMap<LanePoints.Path,String>(LanePoints.Path.class);
    for(var kind:options.path()==LanePoints.Path.AUTO?List.of(LanePoints.Path.DIRECT,LanePoints.Path.RIGHT,LanePoints.Path.LEFT,LanePoints.Path.LEFT_LOOP):List.of(options.path()))for(double lift:loopLifts(kind,options.elevation()))try{
      out.add(new Candidate(kind,generate(a,b,settings,options,kind,lift)));
    }catch(IllegalArgumentException e){errors.put(kind,e.getMessage());}
    if(out.isEmpty())throw new IllegalArgumentException(failure(a,b,options,errors));
    out.sort(Comparator.comparingDouble(c->c.mesh().length()));return List.copyOf(out);
  }
  private static double[] loopLifts(LanePoints.Path path,LanePoints.Elevation elevation){
    if(path!=LanePoints.Path.LEFT_LOOP||elevation==LanePoints.Elevation.KEEP)return new double[]{0};
    if(elevation==LanePoints.Elevation.OVER)return new double[]{0,6,8,10,12,16,24};
    if(elevation==LanePoints.Elevation.UNDER)return new double[]{0,-6,-8,-10,-12,-16,-24};
    return new double[]{0,6,-6,8,-8,10,-10,12,-12,16,-16,24,-24};
  }
  private static List<Candidate> expanded(Port a,Port b,Settings settings,LanePoints.Options o){
    List<Sample> prefix=o.sourceExtra()?approach(a,true,settings.width(),o.transition()):List.of();
    List<Sample> suffix=o.targetExtra()?approach(b,false,settings.width(),o.transition()):List.of();
    Port start=prefix.isEmpty()?a:approachPort(prefix,false,a),end=suffix.isEmpty()?b:approachPort(suffix,true,b);
    var plain=o.withoutApproaches();
    var result=new ArrayList<Candidate>();
    String error="扩出后的路径净空不足";
    for(var c:candidates(start,end,settings,plain))try{
      var samples=new ArrayList<Sample>();append(samples,prefix);append(samples,c.mesh().samples());append(samples,suffix);
      Mesh mesh=RoadRibbon.mesh(samples,settings);RoadRibbon.checkSelfIntersections(mesh,4);checkVolume(mesh);result.add(new Candidate(c.path(),mesh));
    }catch(IllegalArgumentException e){error=e.getMessage();}
    if(result.isEmpty())throw new IllegalArgumentException(error);
    return List.copyOf(result);
  }
  private static List<Sample> approach(Port p,boolean source,double width,double transition){
    if(!p.approach().isEmpty())return p.approach();
    double total=LaneRampApproach.length(transition);int n=(int)Math.ceil(total/.5);var out=new ArrayList<Sample>();
    for(int i=0;i<=n;i++){double d=total*i/n,q=source?d:total-d,grow=Settings.smooth(Math.min(1,q/LaneRampApproach.taper(transition)));
      V position=p.position().add(p.direction().mul(source?d:d-total)).add(p.outside().mul(p.extraWidth()*grow));
      out.add(new Sample(position,p.direction().left(),d,width/2));}
    return out;
  }
  private static Port approachPort(List<Sample> samples,boolean first,Port p){
    var s=samples.get(first?0:samples.size()-1);var a=samples.get(first?0:samples.size()-2);var b=samples.get(first?1:samples.size()-1);V d=b.center().sub(a.center());
    return new Port(s.center(),s.left().left().mul(-1),p.outside(),p.extraWidth(),d.y()/Math.max(.001,d.horizontalLength()));
  }
  private static void append(List<Sample> out,List<Sample> added){for(var s:added){double d=out.isEmpty()?0:s.center().sub(out.get(out.size()-1).center()).horizontalLength();if(!out.isEmpty()&&d<1e-7)continue;out.add(new Sample(s.center(),s.left(),out.isEmpty()?0:out.get(out.size()-1).distance()+d,s.halfWidth()));}}
  public static String failure(Port a,Port b,LanePoints.Options options,Map<LanePoints.Path,String> errors){
    var preferred=options.path();
    if(preferred==LanePoints.Path.AUTO){double turn=positive(angle(b.direction())-angle(a.direction()));preferred=a.direction().dot(b.direction())>.75?LanePoints.Path.DIRECT:turn<=Math.PI?LanePoints.Path.RIGHT:LanePoints.Path.LEFT;}
    String error=errors.get(preferred);if(error==null)return "自动未找到可用路径："+String.join("；",errors.values());
    return (options.path()==LanePoints.Path.AUTO?"自动 / ":"")+preferred.label+"："+error;
  }
  private static Mesh generate(Port a,Port b,Settings settings,LanePoints.Options o,LanePoints.Path kind,double lift){
    if(kind==LanePoints.Path.LEFT){
      Mesh mirrored=generate(reflect(a),reflect(b),settings,o,LanePoints.Path.RIGHT,lift);
      var samples=mirrored.samples().stream().map(s->new Sample(reflect(s.center()),reflect(s.left()).mul(-1),s.distance(),s.halfWidth())).toList();
      return RoadRibbon.mesh(samples,settings);
    }
    if(a.position().sub(b.position()).horizontalLength()>2048)throw new IllegalArgumentException("匝道端点距离不能超过 2048 格");
    double transition=o.transition();V pa=a.position(),pb=b.position();List<Frame> frames=new ArrayList<>();
    double leadA=transition,leadB=transition;
    if(kind==LanePoints.Path.RIGHT){
      double turn=positive(angle(b.direction())-angle(a.direction()));
      if(turn>Math.PI+1e-5)throw new IllegalArgumentException("所选车道方向需要左转，右转样式不能反转车道方向");
      double cross=a.direction().x()*b.direction().z()-a.direction().z()*b.direction().x();
      if(cross>1e-6){
        V delta=pb.sub(pa);double tangent=o.radius()*Math.tan(turn/2);
        double availableA=(delta.x()*b.direction().z()-delta.z()*b.direction().x())/cross-tangent;
        double availableB=(a.direction().x()*delta.z()-a.direction().z()*delta.x())/cross-tangent;
        // Retain the requested radius and explicit extra-lane tapers; shorten only optional straight leads.
        leadA=Math.min(transition,Math.max(0,availableA));
        leadB=Math.min(transition,Math.max(0,availableB));
      }
    }
    if(kind!=LanePoints.Path.DIRECT){V lead=pa.add(a.direction().mul(leadA));if(leadA>1e-6)line(frames,pa,lead,a.direction());pa=lead;pb=pb.sub(b.direction().mul(leadB));}
    if(kind==LanePoints.Path.DIRECT){
      V delta=pb.sub(pa);if(delta.dot(a.direction())<2||delta.dot(b.direction())<2||a.direction().dot(b.direction())<.75)throw new IllegalArgumentException("直接连接需要两端大致同向且前方有过渡空间；请尝试自动或回环");
      bezier(frames,pa,a.direction(),pb,b.direction(),Math.max(4,Math.min(delta.horizontalLength()*.42,Math.max(o.radius(),transition))));
    }else{
      // Right uses an external tangent (RSR); the left loop uses an internal
      // tangent (RSL) with a long right arc and a small left alignment arc.
      double r=o.radius();V ca=pa.add(a.direction().left().mul(r)),cb=pb.add(b.direction().left().mul(kind==LanePoints.Path.LEFT_LOOP?-r:r));
      V delta=cb.sub(ca);
      if(kind==LanePoints.Path.RIGHT&&delta.horizontalLength()<1e-6){
        arc(frames,ca,r,angle(a.direction()),positive(angle(b.direction())-angle(a.direction())));
      }else{
      if(delta.horizontalLength()<1e-6)throw new IllegalArgumentException("回环圆心重合，请调整半径或点位");V tangent=delta.horizontalUnit();if(kind==LanePoints.Path.LEFT_LOOP){double length=delta.horizontalLength();if(length<=2*r)throw new IllegalArgumentException("回环内公切线空间不足，请扩大间距");double theta=angle(tangent)+Math.asin(2*r/length);tangent=new V(Math.cos(theta),0,Math.sin(theta));}
      double aa=angle(a.direction()),bb=angle(b.direction()),tt=angle(tangent),turnA=positive(tt-aa),turnB=positive(kind==LanePoints.Path.LEFT_LOOP?tt-bb:bb-tt),turn=kind==LanePoints.Path.LEFT_LOOP?turnA-turnB:turnA+turnB;
      if(kind==LanePoints.Path.RIGHT&&turn>Math.PI+1e-5)throw new IllegalArgumentException("当前半径或扩出过渡放不进右转空间，请减小半径、关闭扩出或调整选点");
      if(kind==LanePoints.Path.LEFT_LOOP&&turn<Math.PI+1e-5)throw new IllegalArgumentException("当前布局没有左转回环空间，请移动点位或改用其他样式");
      arc(frames,ca,r,aa,turnA);V ta=ca.sub(tangent.left().mul(r)),tb=cb.sub(tangent.left().mul(kind==LanePoints.Path.LEFT_LOOP?-r:r));line(frames,ta,tb,tangent);if(kind==LanePoints.Path.LEFT_LOOP)arcLeft(frames,cb,r,tt,turnB);else arc(frames,cb,r,tt,turnB);
      }
    }
    if(kind!=LanePoints.Path.DIRECT)line(frames,pb,b.position(),b.direction());
    if(frames.size()<2)throw new IllegalArgumentException("连接距离过短");
    // Explicit endpoint frames prevent sampled-chord headings from reversing either lane.
    frames.set(0,new Frame(a.position(),a.direction()));frames.set(frames.size()-1,new Frame(b.position(),b.direction()));
    double[] d=new double[frames.size()];for(int i=1;i<d.length;i++)d[i]=d[i-1]+frames.get(i).p().sub(frames.get(i-1).p()).horizontalLength();
    double length=d[d.length-1];if(length<4)throw new IllegalArgumentException("连接距离须至少 4 格");
    List<Sample> samples=new ArrayList<>();double lastY=0;
    for(int i=0;i<frames.size();i++){double t=d[i]/length,u=1-t;double y=(2*t*t*t-3*t*t+1)*a.position().y()+(t*t*t-2*t*t+t)*length*a.grade()+(-2*t*t*t+3*t*t)*b.position().y()+(t*t*t-t*t)*length*b.grade()+lift*Math.pow(Math.sin(Math.PI*t),2);
      if(i>0&&Math.abs(y-lastY)>.15001*(d[i]-d[i-1]))throw new IllegalArgumentException("匝道坡度超过 15%，请扩大间距或降低高差");lastY=y;
      var f=frames.get(i);samples.add(new Sample(new V(f.p().x(),y,f.p().z()),f.d().left(),d[i],settings.width()/2));
    }
    Mesh mesh=RoadRibbon.mesh(samples,settings);if(RoadRibbon.minRadius(mesh)<Math.max(settings.width()/2+.5,3))throw new IllegalArgumentException("接头内侧半径不足，请增大过渡长度");RoadRibbon.checkSelfIntersections(mesh,4);checkVolume(mesh);return mesh;
  }
  public static void checkVolume(Mesh mesh){var p=mesh.samples();double width=mesh.samples().stream().mapToDouble(s->s.halfWidth()*2).max().orElse(mesh.settings().width()),clearance=4+mesh.settings().thickness();for(int i=0;i<p.size();i+=3)for(int j=i+3;j<p.size();j+=3){var a=p.get(i);var b=p.get(j);if(b.distance()-a.distance()<width*3)continue;double horizontal=a.center().sub(b.center()).horizontalLength();if(horizontal<a.halfWidth()+b.halfWidth()+.35&&Math.abs(a.center().y()-b.center().y())<clearance-.05)throw new IllegalArgumentException("回环路面体积净空不足，请增大半径、间距或过渡长度");}}
  private static V reflect(V p){return new V(p.x(),p.y(),-p.z());}
  private static Port reflect(Port p){return new Port(reflect(p.position()),reflect(p.direction()),reflect(p.outside()),p.extraWidth(),p.grade());}
  private static double angle(V d){return Math.atan2(d.z(),d.x());}
  private static double positive(double a){double v=a%(2*Math.PI);if(v<0)v+=2*Math.PI;return v<1e-10||2*Math.PI-v<1e-10?0:v;}
  private static void add(List<Frame> f,V p,V d){if(!f.isEmpty()&&f.get(f.size()-1).p().sub(p).horizontalLength()<1e-6)return;if(f.size()>=RoadLimits.MAX_SAMPLES)throw new IllegalArgumentException("匝道过长");f.add(new Frame(p,d));}
  private static void arc(List<Frame> f,V c,double r,double start,double turn){int n=Math.max(1,(int)Math.ceil(r*turn/.65));for(int i=0;i<=n;i++){double t=start+turn*i/n;V d=new V(Math.cos(t),0,Math.sin(t));add(f,c.sub(d.left().mul(r)),d);}}
  private static void arcLeft(List<Frame> f,V c,double r,double start,double turn){int n=Math.max(1,(int)Math.ceil(r*turn/.65));for(int i=0;i<=n;i++){double t=start-turn*i/n;V d=new V(Math.cos(t),0,Math.sin(t));add(f,c.add(d.left().mul(r)),d);}}
  private static void line(List<Frame> f,V a,V b,V dir){int n=Math.max(1,(int)Math.ceil(a.sub(b).horizontalLength()/.65));for(int i=0;i<=n;i++)add(f,a.add(b.sub(a).mul((double)i/n)),dir);}
  private static void bezier(List<Frame> f,V a,V da,V b,V db,double k){V p=a.add(da.mul(k)),q=b.sub(db.mul(k));int n=Math.max(8,(int)Math.ceil((a.sub(p).horizontalLength()+p.sub(q).horizontalLength()+q.sub(b).horizontalLength())/.55));for(int i=0;i<=n;i++){double t=(double)i/n,u=1-t;V v=a.mul(u*u*u).add(p.mul(3*u*u*t)).add(q.mul(3*u*t*t)).add(b.mul(t*t*t));V d=p.sub(a).mul(u*u).add(q.sub(p).mul(2*u*t)).add(b.sub(q).mul(t*t));if(d.horizontalLength()<1e-7)throw new IllegalArgumentException("匝道过渡折返");add(f,v,d.horizontalUnit());}}
  private LaneRampPaths(){}
}
