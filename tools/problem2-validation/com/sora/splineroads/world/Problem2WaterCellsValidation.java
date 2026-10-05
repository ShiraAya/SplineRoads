package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import java.util.*;
/** Actual road index and shell raster; hypothetical ambient water, not live FluidState. */
public final class Problem2WaterCellsValidation {
  static int checks,fixtures;static long next=1;
  static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  static RoadStructures.Ground ground=new RoadStructures.Ground(){public double top(double x,double z,double y){return y-20;}public boolean blocked(RoadStructures.Part p){return false;}public boolean joined(V p){return false;}};
  public static void main(String[]args){
    for(var type:RoadInfrastructure.Tunnel.values())for(double angle:List.of(0.0,.61))for(double y:List.of(2.0,341.0)){
      var o=RoadProfile.Options.DEFAULT.infrastructure(RoadInfrastructure.Config.DEFAULT.tunnel(type));
      var s=new Settings(Mode.STRAIGHT,Style.O4_RAIL,RoadProfile.width(Style.O4_RAIL,o,4),1,.35,90).options(o).structure(Structure.TUNNEL);
      V a=new V(-100.2,y,32.3),dir=new V(Math.sin(angle),.02,Math.cos(angle)),b=a.add(dir.mul(48));var na=new Node(a,RoadPlanner.yaw(dir),.02);var nb=new Node(b,na.yaw(),.02);
      var r=new RoadRecord(new UUID(2,next++),new UUID(2,0),new BlockPos((int)Math.floor(a.x()),(int)Math.floor(a.y()),(int)Math.floor(a.z())),new BlockPos((int)Math.floor(b.x()),(int)Math.floor(b.y()),(int)Math.floor(b.z())),na,nb,s);r=r.structures(RoadInfrastructure.plan(r.mesh(),ground));
      var eager=new RoadIndex.Built(r);eager.cells.size();var lazy=new RoadIndex.Built(r);int retained=0,dry=0;
      for(long key:eager.shellCells){var p=BlockPos.of(key);var c=eager.column(p);boolean slab=c!=null&&p.getY()+1>c.minTop()-s.thickness()+1e-7&&p.getY()<c.maxTop()-1e-7;
        boolean interior=eager.clearanceAt(p);boolean open=RoadWaterPolicy.permeable(slab,false,true,true,interior);
        check(lazy.clearanceAt(p)==interior&&lazy.shellAt(p),"eager/lazy shell boundary mismatch");
        if(interior||slab){check(!open,"water entered actual dry interior or slab");dry++;}else{check(open,"actual outside partial lining still drains");retained++;}
      }
      check(retained>0&&dry>0,"fixture did not contain both wet exterior and dry boundary");check(!lazy.rasterized(),"local query forced full raster");fixtures++;
      System.out.println("  "+type+" yaw="+angle+" Y="+y+": exterior="+retained+", protected="+dry);
    }
    System.out.println("Problem2WaterCellsValidation: "+fixtures+" real shell/index layouts, "+checks+" checks PASS; ambient water assumed, no live fluid tick or game runtime.");
  }
}
