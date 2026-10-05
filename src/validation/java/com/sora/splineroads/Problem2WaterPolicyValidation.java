package com.sora.splineroads;
import com.sora.splineroads.core.RoadWaterPolicy;
/** Tests cell classification, not a Minecraft fluid simulation. */
public final class Problem2WaterPolicyValidation {
  public static void main(String[]args){int checks=0;
    for(int mask=0;mask<256;mask++){
      boolean slab=(mask&1)!=0,fill=(mask&2)!=0,tube=(mask&4)!=0,shell=(mask&8)!=0,dry=(mask&16)!=0;
      boolean terrainWater=(mask&32)!=0,previousWater=(mask&64)!=0,originalWater=(mask&128)!=0;
      boolean p=RoadWaterPolicy.permeable(slab,fill,tube,shell,dry);
      boolean w=RoadWaterPolicy.waterlogged(p,terrainWater,previousWater,originalWater);
      if((slab||fill||dry)&&(p||w))throw new AssertionError("protected volume admitted fluid");
      if(!slab&&!fill&&!dry&&tube&&shell&&!p)throw new AssertionError("exterior shell blanket-drained");
      if(!slab&&!fill&&!dry&&tube&&!shell&&p)throw new AssertionError("non-shell tunnel cell unexpectedly opened");
      if(w!=(p&&(terrainWater||previousWater||originalWater)))throw new AssertionError("lost existing or tracked original water");checks+=4;
    }
    System.out.println("Problem2WaterPolicyValidation: "+checks+" checks / 256 combinations PASS; exterior shell retained, slab/interior dry. No fluid ticks/GPU/world run.");
  }
}
