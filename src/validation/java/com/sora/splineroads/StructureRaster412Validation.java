package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Compares the new one-pass metadata against the actual old sequence of raster calls.
 * Timings are same-host core microbenchmarks, NOT Minecraft construction/FPS figures. */
public final class StructureRaster412Validation {
  static int checks;static volatile long sink;
  static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
  static RoadStructureRaster.Result legacy(List<Part> parts){
    var cells=RoadRaster.structures(parts,null);Set<RoadRaster.Cell> lights=new HashSet<>(),shells=new HashSet<>();Map<RoadRaster.Cell,Double> tops=new HashMap<>();int calls=parts.size();
    for(var p:parts)if(p.luminous()){lights.addAll(RoadRaster.structures(List.of(p),null).keySet());calls++;}
    for(var p:parts)if(p.material()==Material.TUNNEL){shells.addAll(RoadRaster.structures(List.of(p),null).keySet());calls++;}
    for(var p:parts)if(p.material().name().startsWith("WALK_")){RoadRaster.structures(List.of(p),null).forEach((c,b)->tops.merge(c,b.stream().mapToDouble(v->c.y()+v.y1()).max().orElse(c.y()),Math::max));calls++;}
    return new RoadStructureRaster.Result(cells,lights,shells,tops,calls);
  }
  static List<Part> fixture(int seed,int count){
    var random=new Random(seed);var parts=new ArrayList<Part>();var materials=new Material[]{Material.TUNNEL,Material.WALK_STONE_BRICKS,Material.LAMP,Material.CONCRETE,Material.DEFAULT};
    for(int i=0;i<count;i++){double x=-30+random.nextDouble()*60,z=-30+random.nextDouble()*60,y=60+random.nextDouble()*8;
      boolean pier=i%7==0;var a=new V(x,y,z);var b=pier?a:new V(x+random.nextDouble()*5,y+(i%2==0?0:random.nextDouble()),z+random.nextDouble()*5);
      parts.add(new Part(a,b,.35+random.nextDouble()*1.5,.2+random.nextDouble()*3,pier,materials[i%materials.length]));
    }
    return List.copyOf(parts);
  }
  public static void main(String[] args){
    check(RoadStructureRaster.build(List.of()).cells().isEmpty(),"empty input");
    for(int i=0;i<32;i++){var parts=fixture(i,24);var old=legacy(parts);var now=RoadStructureRaster.build(parts);
      check(old.cells().equals(now.cells()),"exact ordered collision boxes");check(old.lights().equals(now.lights()),"luminous membership");check(old.shells().equals(now.shells()),"shell membership");check(old.walkTops().equals(now.walkTops()),"walk top maxima");check(now.rasterizedParts()==parts.size(),"one pass per part");
      var reversed=new ArrayList<>(parts);Collections.reverse(reversed);check(legacy(reversed).cells().equals(RoadStructureRaster.build(reversed).cells()),"reversed input order preserved");
      var dup=new ArrayList<>(parts);dup.add(parts.get(0));check(legacy(dup).cells().equals(RoadStructureRaster.build(dup).cells()),"intentional overlapping duplicate preserved");
    }
    var parts=fixture(412,160);for(int i=0;i<3;i++){sink+=legacy(parts).cells().size();sink+=RoadStructureRaster.build(parts).cells().size();}
    long[] oldTimes=new long[7],newTimes=new long[7];for(int i=0;i<7;i++){
      if(i%2==0){oldTimes[i]=timed(parts,true);newTimes[i]=timed(parts,false);}else{newTimes[i]=timed(parts,false);oldTimes[i]=timed(parts,true);}
    }
    System.out.println("StructureRaster412Validation: "+checks+" checks passed; 32 fixtures, exact collision/metadata and ordering parity. No world/GPU test.");
    System.out.println("fixture parts="+parts.size()+", old part raster passes="+legacy(parts).rasterizedParts()+", new passes="+parts.size());
    System.out.println("old milliseconds="+Arrays.toString(Arrays.stream(oldTimes).mapToDouble(v->v/1e6).toArray()));
    System.out.println("new milliseconds="+Arrays.toString(Arrays.stream(newTimes).mapToDouble(v->v/1e6).toArray()));
    Arrays.sort(oldTimes);Arrays.sort(newTimes);System.out.printf(Locale.ROOT,"median old %.3f ms, new %.3f ms. Core-only; no whole-operation speedup claim.%n",oldTimes[3]/1e6,newTimes[3]/1e6);
  }
  static long timed(List<Part> parts,boolean old){long t=System.nanoTime();var result=old?legacy(parts):RoadStructureRaster.build(parts);sink+=result.cells().size()+result.walkTops().size();return System.nanoTime()-t;}
}
