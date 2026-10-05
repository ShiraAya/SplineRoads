package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

public final class Revision292Validation {
  static int checks;
  static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
  public static void main(String[] args){
    for(int mode=0;mode<3;mode++){
      var s=new Settings(Mode.STRAIGHT,Style.O2_YELLOW,11,1,.4,90);
      var a=new Node(new V(-199.5,20,-12.5),-90,0);var b=new Node(new V(200.5,mode==0?20:36,mode==2?110.5:-12.5),-90,0);
      var mesh=RoadGeometry.build(a,b,s);
      var parts=List.of(new RoadStructures.Part(new V(-14,0,-12),new V(-14,0,-12),2,20,true,RoadStructures.Material.CONCRETE),
          new RoadStructures.Part(new V(-30,35,-40),new V(10,35,30),1.3,1,false,RoadStructures.Material.STEEL));
      var full=new HashSet<>(RoadRaster.raster(mesh).keySet());full.addAll(RoadRaster.structures(parts,null).keySet());
      var local=new RoadRaster.Local(mesh,parts);
      long started=System.nanoTime();
      for(int cx=-3;cx<=1;cx++)for(int cz=-3;cz<=2;cz++){
        int x=cx,z=cz;var expected=new HashSet<RoadRaster.Cell>();for(var c:full)if(Math.floorDiv(c.x(),16)==x&&Math.floorDiv(c.z(),16)==z)expected.add(c);
        check(local.cellsInChunk(cx,cz).equals(expected),"chunk cells match full raster, mode="+mode+" chunk="+cx+","+cz);
      }
      System.out.printf(Locale.ROOT,"Chunk-local geometry mode %d: 30 chunk queries in %.2f ms%n",mode,(System.nanoTime()-started)/1e6);
    }
    System.out.println("REVISION 292 GEOMETRY PASS: "+checks+" checks");
  }
}
