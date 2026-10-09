package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import java.lang.reflect.*;

/** Compare the existing exact predicates with their spatially indexed form.
 * Includes valid curves, flat self-crossings and grade-separated crossings. */
public final class Planner443Benchmark {
  static int outcome(Method m,Mesh mesh,Object... tail)throws Exception{
    Object[] args=new Object[tail.length+1];args[0]=mesh;System.arraycopy(tail,0,args,1,tail.length);
    try{m.invoke(null,args);return 0;}catch(InvocationTargetException e){if(e.getCause() instanceof IllegalArgumentException)return 1;throw e;}
  }
  public static void main(String[] args)throws Exception{
    Method slowA=RoadRibbon.class.getDeclaredMethod("shortSelfIntersections",Mesh.class,double.class),fastA=RoadRibbon.class.getMethod("checkSelfIntersections",Mesh.class,double.class);
    Method slowB=LaneRampPaths.class.getDeclaredMethod("shortVolume",Mesh.class),fastB=LaneRampPaths.class.getMethod("checkVolume",Mesh.class);slowA.setAccessible(true);slowB.setAccessible(true);
    var fixtures=new ArrayList<Mesh>();int checks=0;
    for(int n:new int[]{97,240,500,1000})for(int kind=0;kind<3;kind++)for(int seed=0;seed<4;seed++){
      var points=new ArrayList<Sample>();
      for(int i=0;i<n;i++){
        double t=i/(double)(n-1),x,z,y=20;
        if(kind==0){x=(10+seed)*Math.sin(t*5);z=t*n*.55;}
        else {x=60*Math.sin(t*Math.PI*2);z=50*Math.sin(t*Math.PI*4);if(kind==2)y+=t*24;}
        V p=new V(x+seed*100000,y,z);V tangent=i==0?new V(0,0,1):p.sub(points.get(i-1).center()).horizontalUnit();
        points.add(new Sample(p,tangent.left(),i*.55,2));
      }
      var first=points.get(0);points.set(0,new Sample(first.center(),points.get(1).left(),first.distance(),first.halfWidth()));
      var mesh=RoadRibbon.mesh(points,new Settings(Mode.CURVE,Style.C1_RAMP,4,1,.35,90));
      if(outcome(slowA,mesh,4d)!=outcome(fastA,mesh,4d)||outcome(slowB,mesh)!=outcome(fastB,mesh))throw new AssertionError("indexed predicate mismatch");checks+=2;
      if(kind==0&&n>=500)fixtures.add(mesh);
    }
    for(int warm=0;warm<6;warm++)for(var m:fixtures){outcome(slowA,m,4d);outcome(fastA,m,4d);outcome(slowB,m);outcome(fastB,m);}
    double[] old=new double[5],indexed=new double[5];
    for(int run=0;run<5;run++)for(int pass=0;pass<2;pass++){
      boolean slow=(run+pass)%2==0;long begin=System.nanoTime();
      for(int rep=0;rep<8;rep++)for(var m:fixtures){outcome(slow?slowA:fastA,m,4d);outcome(slow?slowB:fastB,m);}
      (slow?old:indexed)[run]=(System.nanoTime()-begin)/1e6;
    }
    Arrays.sort(old);Arrays.sort(indexed);
    System.out.printf(Locale.ROOT,"Planner443Benchmark equivalence=%d; exact checks median old_ms=%.3f indexed_ms=%.3f speedup=%.2fx%n",checks,old[2],indexed[2],old[2]/indexed[2]);
  }
}
