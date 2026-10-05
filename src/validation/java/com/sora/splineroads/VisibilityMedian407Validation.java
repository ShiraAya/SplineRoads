package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Real CPU geometry/policy. No actual game view, chunks, driver or GPU is simulated. */
public final class VisibilityMedian407Validation {
  static int checks,cases;static final double LENGTH=192;
  static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  static final Ground GROUND=new Ground(){public double top(double x,double z,double y){return y;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
  static Settings settings(Style st,Structure structure,boolean left){var o=RoadProfile.Options.DEFAULT.traffic(left);return new Settings(Mode.STRAIGHT,st,RoadProfile.width(st,o,4),1,.35,90).options(o).structure(structure);}
  static Mesh mesh(Settings s){return RoadGeometry.build(new Node(new V(0,100,0),0,0),new Node(new V(0,100,LENGTH),0,0),s);}
  static boolean oldVisibility(double dx,double dy,double dz,int chunks){double r=chunks*16+32;return dx*dx+dy*dy+dz*dz<r*r;}
  static void visibility(){
    check(!oldVisibility(160,0,160,8),"old radius removed a diagonal visible-column fixture");
    check(RoadVisibility.within(160,160,176,176,0,0,8),"loaded-square corner conservatively retained");
    check(!oldVisibility(0,240,0,8)&&RoadVisibility.within(-8,-8,8,8,0,0,8),"altitude doesn't remove tunnel cover under camera");
    for(int chunks:new int[]{2,4,8,16,32})for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(double translation:new double[]{0,1000000,-1000000}){
      double range=chunks*16.0+32,x=translation+dx*(range-1),z=translation+dz*(range-1);
      check(RoadVisibility.within(x,z,x+1,z+1,translation,translation,chunks),"sign/large-coordinate range");
      check(!RoadVisibility.within(translation+range+1,z,translation+range+10,z+10,translation,translation,chunks),"out-of-range horizontal work still culled");
    }
    check(!RoadVisibility.within(Double.NaN,0,1,1,0,0,8),"NaN fails closed");
    System.out.println("View-policy reproduction: old 3D circle rejects diagonal and altitude fixtures; conservative horizontal window keeps them. Frustum culling remains in production renderer.");
  }
  static List<String> cross(List<Part> parts,double station){var out=new ArrayList<String>();for(var p:parts){if(p.pier()||p.b().sub(p.a()).horizontalLength()<1)continue;double lo=Math.min(p.a().z(),p.b().z()),hi=Math.max(p.a().z(),p.b().z());if(station<=lo+1e-6||station>=hi-1e-6)continue;out.add(p.material()+":"+Math.round(p.width()*1e6)+":"+Math.round(p.height()*1e6));}Collections.sort(out);return out;}
  static List<Part> median(Mesh m){var out=new ArrayList<Part>();RoadStructures.medianFurniture(m,GROUND,out);return out;}
  static List<String> paintCross(List<RoadSurface.Face> faces,double station){
    var ranges=new TreeMap<Integer,List<double[]>>();
    for(var f:faces){if(f.points().stream().anyMatch(v->Math.abs(v.x())>1.8))continue;double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
      for(int i=0;i<f.points().size();i++){var a=f.points().get(i);var b=f.points().get((i+1)%f.points().size());
        if(station>Math.min(a.z(),b.z())&&station<Math.max(a.z(),b.z())){double t=(station-a.z())/(b.z()-a.z()),x=a.x()+(b.x()-a.x())*t;lo=Math.min(lo,x);hi=Math.max(hi,x);}}
      if(hi>lo+1e-7)ranges.computeIfAbsent(f.color(),k->new ArrayList<>()).add(new double[]{lo,hi});
    }
    var result=new ArrayList<String>();ranges.forEach((color,values)->{values.sort(Comparator.comparingDouble(v->v[0]));double lo=0,hi=0;boolean first=true;
      for(var v:values){if(first){lo=v[0];hi=v[1];first=false;}else if(v[0]<=hi+1e-7)hi=Math.max(hi,v[1]);else{result.add(color+":"+Math.round(lo*1e4)+":"+Math.round(hi*1e4));lo=v[0];hi=v[1];}}
      if(!first)result.add(color+":"+Math.round(lo*1e4)+":"+Math.round(hi*1e4));});return result;
  }
  static void transitions(){
    for(Style a:List.of(Style.O4_YELLOW,Style.O4_RAIL,Style.O4_GREEN,Style.H4_RAIL,Style.H4_GREEN))for(Style b:List.of(Style.O4_YELLOW,Style.O4_RAIL,Style.O4_GREEN,Style.H4_RAIL,Style.H4_GREEN))for(boolean left:new boolean[]{false,true}){
      var tunnel=settings(a,Structure.TUNNEL,left);var road=settings(b,Structure.GROUND,left);var shared=RoadTransitions.common(tunnel,road);
      check(shared.equals(RoadTransitions.common(road,tunnel)),"construction-order shared section");
      var first=mesh(RoadTransitions.ends(tunnel,shared,null));var last=mesh(RoadTransitions.ends(tunnel,null,shared));
      var x=median(first);var y=median(last);
      for(double d=.75;d<LENGTH;d+=2)check(cross(x,d).equals(cross(y,LENGTH-d)),"mirrored median material/width at "+a+"/"+b+" @"+d);
      var firstPaint=RoadSurface.build(first,List.of(),List.of()).markings();var lastPaint=RoadSurface.build(last,List.of(),List.of()).markings();
      for(double d=.37;d<LENGTH;d+=2)check(paintCross(firstPaint,d).equals(paintCross(lastPaint,LENGTH-d)),"mirrored central paint differs "+a+"/"+b+" @"+d+" "+paintCross(firstPaint,d)+" vs "+paintCross(lastPaint,LENGTH-d));
      var actual=RoadInfrastructure.plan(first,GROUND);for(var part:x)check(actual.contains(part),"tunnel generator bypasses selected median transition");
      check(actual.stream().noneMatch(p->p.material()==Material.GREEN||p.material()==Material.SOIL),"tunnel must never contain a planted median");
      if(a==Style.O4_GREEN)check(actual.stream().anyMatch(p->p.material()==Material.STEEL),"green selection must become rail in tunnel");
      if(a==Style.O4_RAIL)check(actual.stream().anyMatch(p->p.material()==Material.STEEL),"selected rail replaced by narrow generic bar");
      cases++;
    }
  }
  public static void main(String[]args){visibility();transitions();System.out.println("VisibilityMedian407Validation: "+cases+" directional tunnel/road median cases, "+checks+" checks PASS. CPU policy/geometry only, NOT user screenshot/GPU/actual world validation.");}
}
