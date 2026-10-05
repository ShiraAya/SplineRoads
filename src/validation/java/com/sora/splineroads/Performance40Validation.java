package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual production core. No GPU, Forge, shader, NBT or FPS claims. */
public final class Performance40Validation {
  private static int checks;
  private static void check(boolean p,String m){checks++;if(!p)throw new AssertionError(m);}
  private static void near(double a,double b,double e,String m){check(Math.abs(a-b)<=e,m+": "+a+" / "+b);}
  public static void main(String[] args)throws Exception {cache();terrain();System.out.println("Performance40Validation: "+checks+" checks passed (production core; not a shader/GPU test)");}
  private static void cache()throws Exception {
    var calls=new AtomicInteger();var cache=new WeakIdentityCache<Object,String>(2,6,String::length);
    Object a=new String("same"),b=new String("same"),c=new Object();
    check(cache.get(a,k->{calls.incrementAndGet();return "aa";}).equals("aa"),"compute");
    check(cache.get(a,k->{throw new AssertionError("cache miss");}).equals("aa"),"identity hit");
    cache.get(b,k->{calls.incrementAndGet();return "bb";});check(calls.get()==2,"equal values are different identity keys");
    cache.get(c,k->"cc");check(cache.stats().entries()==2,"entry cap");
    cache.get(a,k->{calls.incrementAndGet();return "aa";});check(calls.get()==3,"LRU eviction");
    Object huge=new Object();cache.get(huge,k->"hugeval");check(cache.stats().entries()<=2&&cache.stats().weight()<=6,"weighted cap");
    var started=new CountDownLatch(1);var finish=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();
    try {var future=executor.submit(()->cache.get(huge,k->{started.countDown();try{finish.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}return "abc";}));
      check(started.await(2,TimeUnit.SECONDS),"work started without holding cache lock");cache.clear();finish.countDown();check(future.get(2,TimeUnit.SECONDS).equals("abc"),"computation can finish after clear");check(cache.stats().entries()==0,"old epoch cannot republish");
    }finally{finish.countDown();executor.shutdownNow();}
  }
  private static Mesh road(V a,V b){var d=b.sub(a);var n=new Node(a,RoadPlanner.yaw(d.horizontalUnit()),d.y()/d.horizontalLength());var end=new Node(b,n.yaw(),n.grade());return RoadGeometry.build(n,end,new Settings(Mode.STRAIGHT,Style.O2_ONE,9,1,.35,90));}
  private static void terrain() {
    for(double origin:new double[]{0,-100000,20000000})for(double grade:new double[]{0,.08,-.08}) {
      Mesh mesh=road(new V(origin,100,origin),new V(origin+21,100+grade*70,origin+70));
      var surface=RoadSurface.build(mesh,List.of(),List.of());var result=RoadTerrainMesh.build(surface);
      check(!result.cells().isEmpty(),"cells exist");
      double before=surface.pavement().stream().filter(RoadTerrainMesh::asphalt).mapToDouble(f->RoadTerrainMesh.area(f.points())).sum();
      double after=result.cells().values().stream().flatMap(Collection::stream).filter(p->!p.paint()).mapToDouble(p->RoadTerrainMesh.area(p.vertices())).sum();
      near(before,after,1e-5,"exact projected asphalt area, no stepped replacement");
      double beforePaint=surface.markings().stream().mapToDouble(f->RoadTerrainMesh.area(f.points())).sum();
      double afterPaint=result.cells().values().stream().flatMap(Collection::stream).filter(RoadTerrainMesh.Polygon::paint).mapToDouble(p->RoadTerrainMesh.area(p.vertices())).sum();
      near(beforePaint,afterPaint,1e-5,"all paint survives clipping");
      var collision=RoadRaster.raster(mesh);
      for(var e:result.cells().entrySet()) {
        var cell=e.getKey();check(collision.containsKey(new RoadRaster.Cell(cell.x(),cell.y(),cell.z())),"each terrain cell has an actual collider: "+cell);
        check(cell.section().x()*16+(cell.x()&15)==cell.x(),"negative section addressing");
        for(var p:e.getValue())for(var v:p.vertices()) {
          check(v.x()>=cell.x()-1e-7&&v.x()<=cell.x()+1+1e-7&&v.z()>=cell.z()-1e-7&&v.z()<=cell.z()+1+1e-7,"horizontal clipping");
          check(v.y()>=cell.y()-1e-7&&v.y()<=cell.y()+1+1e-7,"vertical clipping");
          if(grade==0)check(cell.y()==99,"integer surface owned by block below, never air");
        }
      }
      check(RoadTerrainMesh.remainder(surface).markings().isEmpty(),"paint not drawn twice in VBO");
      check(RoadTerrainMesh.remainder(surface).pavement().stream().noneMatch(RoadTerrainMesh::asphalt),"asphalt not drawn twice");
      check(RoadTerrainMesh.remainder(surface).pavement().size()==surface.pavement().stream().filter(f->!RoadTerrainMesh.asphalt(f)).count(),"underside/sidewalls retained");
      var key=result.cells().keySet().iterator().next();var allowed=Set.of(new RoadTerrainMesh.Chunk(key.x()>>4,key.z()>>4));
      var filtered=RoadTerrainMesh.build(surface,allowed);
      check(filtered.cells().keySet().stream().allMatch(k->allowed.contains(new RoadTerrainMesh.Chunk(k.x()>>4,k.z()>>4))),"loaded-chunk filter");
      check(RoadTerrainMesh.build(surface,Set.of()).cells().isEmpty(),"unloaded road costs zero baked cells");
      try{result.cells().clear();throw new AssertionError("mutable terrain snapshot");}catch(UnsupportedOperationException expected){checks++;}
    }
  }
}
