package com.sora.splineroads.core;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadRaster.*;
import java.util.*;
/** Executable equivalence checks against retained 0.40.18 predicates. */
public final class Performance431Validation {
 static int checks;
 static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
 static String error(Runnable r){try{r.run();return "PASS";}catch(IllegalArgumentException e){return e.getMessage();}}
 static Mesh ribbon(int n,double dy,boolean loop){
  var s=new Settings(Mode.CURVE,Style.C1_RAMP,4,1,.35,90);var p=new ArrayList<Sample>();
  for(int i=0;i<n;i++){double t=(double)i/(n-1),a=t*Math.PI*4,x=loop?30*Math.cos(a):i*.5,z=loop?30*Math.sin(a):8*Math.sin(t*4),y=20+dy*t;
   V side=loop?new V(Math.cos(a),0,Math.sin(a)):new V(0,0,1);
   p.add(new Sample(new V(x,y,z),side,i*.5,2));}
  return new Mesh(List.copyOf(p),s,new V(-32,19,-32),new V(n+32,20+Math.max(0,dy),32),(n-1)*.5,false);
 }
 public static void main(String[]args){
  var rng=new Random(431);
  for(int k=0;k<80;k++){
   var m=ribbon(80+k*3,k%3==0?20:0,k%2==0);
   check(error(()->oldSelf(m,4)).equals(error(()->RoadRibbon.checkSelfIntersections(m,4))),"self-intersection exact predicate "+k);
   check(error(()->oldVolume(m)).equals(error(()->LaneRampPaths.checkVolume(m))),"volume exact predicate "+k);
   var contacts=new ArrayList<LaneRampCorridor.Bound>();var old=new ArrayList<Reference431Corridor.Bound>();
   for(int j=0;j<160;j++){double from=-1+rng.nextDouble()*(m.length()+2),to=from+rng.nextDouble()*4;double amount=rng.nextDouble()*3;boolean over=rng.nextBoolean();
    contacts.add(new LaneRampCorridor.Bound(from,to,amount,over));old.add(new Reference431Corridor.Bound(from,to,amount,over));
    for(int i=0;i<m.samples().size();i++){double previous=m.samples().get(Math.max(0,i-1)).distance(),next=m.samples().get(Math.min(m.samples().size()-1,i+1)).distance();
     check((next>=from-1e-7&&previous<=to+1e-7)==(i>=LaneRampCorridor.firstBoundSample(m,from-1e-7)&&i<=LaneRampCorridor.lastBoundSample(m,to+1e-7)),"interval endpoint coverage");}}
   check(error(()->Reference431Corridor.solveMixed(m,0,m.length(),old,.20)).equals(error(()->LaneRampCorridor.solveMixed(m,0,m.length(),contacts,.20))),"corridor rejection "+k);
  }
  for(int k=0;k<600;k++){
   var boxes=new ArrayList<Box>();for(int x=0;x<8;x++)for(int z=0;z<8;z++)if(rng.nextBoolean())boxes.add(new Box(x/8.,.0,z/8.,(x+1)/8.,.5,(z+1)/8.));
   if(k%3==0)boxes.add(new Box(0,.0,0,.25,.5,.25));
   if(k%5==0)boxes.add(new Box(.125,1e-10,.125,.25,.5,.25));
   Collections.shuffle(boxes,rng);var actual=RoadRaster.compact(boxes);var reference=oldCompact(boxes);
   check(actual.equals(reference),"exact ordered compact boxes "+k+"\n"+actual+"\n"+reference);
  }
  // Real large feasible corridor: every sample is preserved, output equality required.
  int n=6000;var p=new ArrayList<Sample>();var settings=new Settings(Mode.CURVE,Style.C1_RAMP,4,1,.35,90);
  for(int i=0;i<n;i++)p.add(new Sample(new V(i*.25,30,0),new V(0,0,1),i*.25,2));
  var m=RoadRibbon.mesh(p,settings);var bounds=new ArrayList<LaneRampCorridor.Bound>();var references=new ArrayList<Reference431Corridor.Bound>();
  for(int i=0;i<6000;i++){double from=10+(i%2000)*.25,to=m.length()-10;bounds.add(new LaneRampCorridor.Bound(from,to,0,true));references.add(new Reference431Corridor.Bound(from,to,0,true));}
  long start=System.nanoTime();var expected=Reference431Corridor.solveMixed(m,0,m.length(),references,.20);double before=(System.nanoTime()-start)/1e6;
  start=System.nanoTime();var actual=LaneRampCorridor.solveMixed(m,0,m.length(),bounds,.20);double after=(System.nanoTime()-start)/1e6;
  check(actual.samples().equals(expected.samples()),"large feasible corridor every sample equal");
  System.out.printf(Locale.ROOT,"PERF431 corridor samples=%d contacts=%d baseline_ms=%.3f optimized_ms=%.3f EXACT_EQUAL=true%n",n,bounds.size(),before,after);
  // Exact shape cache and defensive results; no hash-only or world-result reuse.
  var small=RoadRibbon.mesh(p.subList(0,160),settings);RoadRaster.clearSavedRasters();
  var original=RoadRaster.raster(small);var first=RoadRaster.cachedRaster(small);
  var equivalent=new Mesh(List.copyOf(new ArrayList<>(small.samples())),small.settings(),small.min(),small.max(),small.length(),small.closed(),small.controlPoint(),small.controls(),small.reference());
  var second=RoadRaster.cachedRaster(equivalent);check(first==second,"equal independent shapes reuse raster");check(first.equals(original),"cached exact raster");
  var changed=RoadRibbon.mesh(p.subList(0,160),new Settings(Mode.CURVE,Style.C1_RAMP,4,1.2,.35,90));check(!RoadRaster.cachedRaster(changed).equals(first),"thickness invalidates");
  try{first.clear();throw new AssertionError("mutable cache map");}catch(UnsupportedOperationException good){checks++;}
  for(int i=0;i<25;i++){var moved=new ArrayList<Sample>();for(var q:small.samples())moved.add(new Sample(q.center().add(new V(0,0,i*7)),q.left(),q.distance(),q.halfWidth()));RoadRaster.cachedRaster(RoadRibbon.mesh(moved,settings));}
  var stats=RoadRaster.savedRasterStats();check(stats[0]<=12&&stats[1]<=250000,"cache bounded by shape and boxes");RoadRaster.clearSavedRasters();check(RoadRaster.savedRasterStats()[0]==0,"cache cleared");
  System.out.println("Performance431Validation: "+checks+" equivalence and cache checks PASS; no live-world claims.");
 }
  public static void oldSelf(Mesh mesh, double clearance) {
    var samples = mesh.samples();
    for (int i = 1; i < samples.size(); i++) {
      RoadPlanningBudget.check();
      V a = samples.get(i - 1).center(), b = samples.get(i).center();
      for (int j = i + 3; j < samples.size(); j++) {
        V c = samples.get(j - 1).center(), d = samples.get(j).center();
        if (Math.max(a.x(), b.x()) < Math.min(c.x(), d.x())
            || Math.min(a.x(), b.x()) > Math.max(c.x(), d.x())
            || Math.max(a.z(), b.z()) < Math.min(c.z(), d.z())
            || Math.min(a.z(), b.z()) > Math.max(c.z(), d.z())) continue;
        double dx = b.x() - a.x(),
            dz = b.z() - a.z(),
            ex = d.x() - c.x(),
            ez = d.z() - c.z(),
            det = dx * ez - dz * ex;
        if (Math.abs(det) < 1e-10) continue;
        double ox = c.x() - a.x(),
            oz = c.z() - a.z(),
            t = (ox * ez - oz * ex) / det,
            u = (ox * dz - oz * dx) / det;
        if (t < 0 || t > 1 || u < 0 || u > 1) continue;
        double first = a.y() + t * (b.y() - a.y()), second = c.y() + u * (d.y() - c.y());
        if (Math.abs(first - second) < clearance + mesh.settings().thickness() - .05)
          throw new IllegalArgumentException("匝道路线上存在净空不足的自交，请调整预设或范围");
      }
    }
  }
  public static void oldVolume(Mesh mesh){var p=mesh.samples();double width=mesh.samples().stream().mapToDouble(s->s.halfWidth()*2).max().orElse(mesh.settings().width()),clearance=4+mesh.settings().thickness();for(int i=0;i<p.size();i+=3){RoadPlanningBudget.check();for(int j=i+3;j<p.size();j+=3){var a=p.get(i);var b=p.get(j);if(b.distance()-a.distance()<width*3)continue;double horizontal=a.center().sub(b.center()).horizontalLength();if(horizontal<a.halfWidth()+b.halfWidth()+.35&&Math.abs(a.center().y()-b.center().y())<clearance-.05)throw new IllegalArgumentException("回环路面体积净空不足，请增大半径、间距或过渡长度");}}}
  public static List<Box> oldCompact(List<Box> input) {
    List<Box> out = new ArrayList<>(input);
    Comparator<Box> order =
        Comparator.comparingDouble(Box::y0)
            .thenComparingDouble(Box::y1)
            .thenComparingDouble(Box::z0)
            .thenComparingDouble(Box::z1)
            .thenComparingDouble(Box::x0)
            .thenComparingDouble(Box::x1);
    out.sort(order);
    for (int axis = 0; axis < 2; axis++) {
      for (int i = 0; i < out.size(); i++)
        for (int j = i + 1; j < out.size(); ) {
          Box a = out.get(i), b = out.get(j);
          boolean height = Math.abs(a.y0() - b.y0()) < 1e-9 && Math.abs(a.y1() - b.y1()) < 1e-9;
          boolean merge =
              height
                  && (axis == 0
                      ? a.z0() == b.z0() && a.z1() == b.z1() && (a.x1() == b.x0() || b.x1() == a.x0())
                      : a.x0() == b.x0() && a.x1() == b.x1() && (a.z1() == b.z0() || b.z1() == a.z0()));
          if (merge) {
            out.set(
                i,
                new Box(
                    Math.min(a.x0(), b.x0()),
                    a.y0(),
                    Math.min(a.z0(), b.z0()),
                    Math.max(a.x1(), b.x1()),
                    a.y1(),
                    Math.max(a.z1(), b.z1())));
            out.remove(j);
            j = i + 1;
          } else j++;
        }
    }
    out.sort(order);
    return out;
  }
}
