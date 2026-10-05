package com.sora.splineroads;
import com.sora.splineroads.core.RoadBoundsIndex;
import com.sora.splineroads.core.RoadBoundsIndex.Bounds;
import java.util.*;

/** Exact old AABB predicate, stable list ordering and dynamic replacement parity.
 * Benchmark counts broad-phase candidates, not actual Minecraft frame or build times. */
public final class RoadBounds413Validation {
  static int checks;static volatile long sink;
  static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
  static List<Integer> brute(List<Bounds> all,Bounds q,double m){var result=new ArrayList<Integer>();for(int i=0;i<all.size();i++)if(all.get(i).overlaps(q,m))result.add(i);return result;}
  static Bounds random(Random r){double x=r.nextDouble()*6000-3000,z=r.nextDouble()*6000-3000;return new Bounds(x,z,x+r.nextDouble()*180,z+r.nextDouble()*180);}
  public static void main(String[]args){
    var random=new Random(413);var all=new ArrayList<Bounds>();for(int i=0;i<1800;i++)all.add(random(random));
    all.add(new Bounds(-10000000,-10000000,10000000,10000000));all.add(new Bounds(64,64,64,64));all.add(new Bounds(-64,-64,-64,-64));
    var index=new RoadBoundsIndex(all);int buckets=index.bucketCount();
    for(int i=0;i<6000;i++){
      if(i%3==0){int slot=random.nextInt(all.size());Bounds next=i%77==0?new Bounds(-100000,-100000,100000,100000):random(random);all.set(slot,next);index.replace(slot,next);}
      var q=i%73==0?new Bounds(-1e7,-1e7,1e7,1e7):random(random);double m=new double[]{0,3,8,82}[i%4];
      check(index.query(q,m).equals(brute(all,q,m)),"replacement/large fallback/order parity");
    }
    var edges=List.of(-128.0,-64.0,0.0,64.0,128.0);
    for(double e:edges)for(double x:List.of(Math.nextDown(e),e,Math.nextUp(e)))for(double margin:List.of(0.0,3.0,82.0)){
      var q=new Bounds(x,-128,x,128);check(index.query(q,margin).equals(brute(all,q,margin)),"inclusive bucket edge and floating point rounding");
    }
    var huge=new RoadBoundsIndex(List.of(new Bounds(-3e7,-3e7,3e7,3e7)));check(huge.bucketCount()==0,"huge footprint uses bounded fallback");check(huge.query(new Bounds(0,0,0,0),0).equals(List.of(0)),"huge road not dropped");
    var coincident=Collections.nCopies(6000,new Bounds(-1,-1,65,65));var denseFallback=new RoadBoundsIndex(coincident);
    for(int i=0;i<40;i++)check(denseFallback.query(new Bounds(0,0,1,1),3).equals(brute(coincident,new Bounds(0,0,1,1),3)),"dense fallback retains full order/results");
    var small=List.of(new Bounds(-1,-1,1,1));var smallIndex=new RoadBoundsIndex(small);check(smallIndex.bucketCount()==0,"small transaction avoids spatial bucket setup");
    var dense=new ArrayList<Bounds>();for(int i=0;i<10000;i++){double x=(i%100)*200,z=(i/100)*200;dense.add(new Bounds(x,z,x+24,z+24));}
    var queries=new ArrayList<Bounds>();for(int i=0;i<2000;i++)queries.add(dense.get((i*37)%dense.size()));
    for(int round=0;round<4;round++){var warm=new RoadBoundsIndex(dense);for(var q:queries){sink+=warm.query(q,82).size();sink+=brute(dense,q,82).size();}}
    long[] scanTimes=new long[5],indexedTimes=new long[5];long candidates=0;for(int round=0;round<5;round++){
      long t=System.nanoTime();for(var q:queries)sink+=brute(dense,q,82).size();scanTimes[round]=System.nanoTime()-t;
      t=System.nanoTime();var fast=new RoadBoundsIndex(dense);for(var q:queries)sink+=fast.query(q,82).size();indexedTimes[round]=System.nanoTime()-t;candidates=fast.examined();
    }
    System.out.println("RoadBounds413Validation: "+checks+" checks; 6000 mutating queries + boundary cases, exact predicate/order. No world runtime.");
    System.out.println("10000 road-bound fixtures, 2000 queries: old predicates=20000000, indexed predicates="+candidates+" (new index construction included in times)");
    System.out.println("scan ms="+Arrays.toString(Arrays.stream(scanTimes).mapToDouble(v->v/1e6).toArray()));System.out.println("indexed ms="+Arrays.toString(Arrays.stream(indexedTimes).mapToDouble(v->v/1e6).toArray()));
    Arrays.sort(scanTimes);Arrays.sort(indexedTimes);System.out.printf(Locale.ROOT,"median scan %.3f ms, indexed %.3f ms. Synthetic sparse broad-phase ONLY; not whole construction speedup.%n",scanTimes[2]/1e6,indexedTimes[2]/1e6);
  }
}
