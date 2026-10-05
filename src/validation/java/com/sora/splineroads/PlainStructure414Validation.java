package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadStructures.*;
/** Untagged batches must keep the original no-metadata fast path. */
public final class PlainStructure414Validation {
  static int checks;
  static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
  public static void main(String[]args){
    for(int seed=0;seed<32;seed++){
      var parts=StructureRaster412Validation.fixture(seed,24).stream().map(p->new Part(p.a(),p.b(),p.width(),p.height(),p.pier(),Material.CONCRETE)).toList();
      var actual=RoadStructureRaster.build(parts);
      check(actual.cells().equals(RoadRaster.structures(parts,null)),"untagged exact original raster");
      check(actual.lights().isEmpty(),"no invented lighting");check(actual.shells().isEmpty(),"no invented shell");check(actual.walkTops().isEmpty(),"no invented walkway");check(actual.rasterizedParts()==parts.size(),"one pass count");
    }
    System.out.println("PlainStructure414Validation: "+checks+" checks, 32 untagged batches retain exact original fast path; no world/GPU test.");
  }
}
