package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import java.util.*;

/** Actual retained-terrain policy and RoadIndex. Minecraft API data types are adapters. */
public final class TunnelTerrain415Validation {
  static int checks;static long serial;
  static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
  static final RoadStructures.Ground GROUND=new RoadStructures.Ground(){public double top(double x,double z,double y){return y-20;}public boolean blocked(RoadStructures.Part p){return false;}public boolean joined(V p){return false;}};
  static RoadIndex.Built road(Structure structure,double y){
    var options=RoadProfile.Options.DEFAULT.infrastructure(RoadInfrastructure.Config.DEFAULT.tunnel(RoadInfrastructure.Tunnel.ARCH));
    var s=new Settings(Mode.STRAIGHT,Style.O8_GREEN,36,1,.35,90).options(options).structure(structure);
    var a=new Node(new V(-20.2,y,12.3),0,0);var b=new Node(new V(-20.2,y,76.3),0,0);
    var r=new RoadRecord(new UUID(415,++serial),new UUID(415,0),new BlockPos(-21,(int)y,12),new BlockPos(-21,(int)y,76),a,b,s,true,4);
    if(structure==Structure.TUNNEL)r=r.structures(RoadInfrastructure.plan(r.mesh(),GROUND));return new RoadIndex.Built(r);
  }
  public static void main(String[]args){
    var tube=road(Structure.TUNNEL,100);var crest=new BlockPos(-21,110,32);var crownColumn=tube.column(crest);
    check(tube.shellAt(crest),"known crown cell must belong to real roof shell");
    check(crest.getY()<crownColumn.maxTop()+RoadInfrastructure.clearance(tube.record.settings()),"reproduce old global-height retention veto");
    check(TunnelTerrainSpace.safe(crest,List.of(tube)),"exact crown exterior can retain original full terrain");
    check(!tube.clearanceAt(crest),"must not reserve extra air layer at integer crown");
    var inside=new BlockPos(-21,109,32);check(!TunnelTerrainSpace.safe(inside,List.of(tube)),"never fill live tunnel interior");
    var other=road(Structure.GROUND,108);
    check(other.boxes(crest).isEmpty()&&other.clearanceAt(crest),"non-owner road fixture actually reserves air");
    check(!TunnelTerrainSpace.safe(crest,List.of(tube,other)),"final snapshot protects non-owner road above tunnel");
    check(TunnelTerrainSpace.safe(crest,List.of(tube)),"removing conflicting neighbor allows terrain again");
    check(!TunnelTerrainSpace.safe(new BlockPos(-21,120,32),List.of(tube)),"cannot fill arbitrary non-shell air");
    int saved=0,denied=0;for(long key:tube.cells.keySet()){
      var p=BlockPos.of(key);if(!tube.shellAt(p))continue;
      boolean safe=TunnelTerrainSpace.safe(p,List.of(tube));boolean interior=RoadTunnelSpace.intersects(tube.mesh,p.getX(),p.getY(),p.getZ(),1);
      check(safe==!interior,"shell retention differs from actual unit-cube/corridor overlap");if(safe)saved++;else denied++;
    }
    check(saved>0&&denied>0,"must test both exterior restoration and interior exclusion");
    System.out.println("TunnelTerrain415Validation: "+checks+" policy/index checks PASS, "+saved+" shell cells retainable / "+denied+" blocked by exact travel. Explicit BlockPos/NBT adapters, no real world writes.");
  }
}
