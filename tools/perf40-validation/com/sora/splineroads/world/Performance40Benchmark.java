package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import java.util.*;

/** Same source can run against 0.39 and 0.40. Microbenchmark, not user-world load time or FPS. */
public final class Performance40Benchmark {
  private static volatile double sink;
  private static RoadRecord road(int i){double x=(i%16)*20,z=(i/16)*90;
    var a=new Node(new V(x,100,z),0,0);var b=new Node(new V(x,100,z+64),0,0);
    return new RoadRecord(new UUID(0,i+1),new UUID(0,9999),new BlockPos((int)x,99,(int)z),new BlockPos((int)x,99,(int)z+64),a,b,
        new Settings(Mode.STRAIGHT,Style.O2_ONE,9,1,.35,90),true,4);
  }
  private static double median(double[] values){Arrays.sort(values);return values[values.length/2];}
  public static void main(String[] args){
    int count=args.length==0?256:Integer.parseInt(args[0]);
    List<RoadRecord> roads=new ArrayList<>();for(int i=0;i<count;i++)roads.add(road(i));
    for(int k=0;k<2;k++)for(var r:roads)sink+=r.mesh().length();
    double[] mesh=new double[5],topology=new double[5];
    for(int k=0;k<5;k++){
      long started=System.nanoTime();for(int repeat=0;repeat<16;repeat++)for(var r:roads)sink+=r.mesh().length();mesh[k]=(System.nanoTime()-started)/1e6;
      var data=new RoadData();for(var r:roads)data.index.put(RoadIndex.Built.loading(r));
      started=System.nanoTime();LaneTopology.initialize(data);topology[k]=(System.nanoTime()-started)/1e6;
      if(data.index.roads.size()!=count)throw new AssertionError("topology changed road count");
    }
    System.out.println("mesh_rounds_ms="+Arrays.toString(mesh)+", endpoint_rounds_ms="+Arrays.toString(topology));
    System.out.printf(Locale.ROOT,"roads=%d, repeated_mesh_median_ms=%.3f, endpoint_initialize_median_ms=%.3f, checksum=%.1f%n",count,median(mesh),median(topology),sink);
    System.out.println("In-memory CPU microbenchmark, five rounds after warmup. NOT NBT disk load, Minecraft FPS or edit wall-clock time.");
  }
}
