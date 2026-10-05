package com.sora.splineroads.world;

import com.sora.splineroads.core.RoadGeometry.Structure;
import com.sora.splineroads.core.RoadTunnelSpace;
import net.minecraft.core.BlockPos;
import java.util.List;

/** Shared construction/manual-infill policy for original terrain around tunnel shells.
 * Retaining a full cube must not fill a live corridor, including a different nearby road.
 * Sources are the FINAL planning snapshot during construction and the live index for infill.
 */
final class TunnelTerrainSpace {
  static boolean safe(BlockPos p,List<RoadIndex.Built> nearby){
    boolean shell=false;
    for(var road:nearby){
      shell|=road.shellAt(p);
      if(road.record.settings().structure()==Structure.TUNNEL){
        if(RoadTunnelSpace.intersects(road.mesh,p.getX(),p.getY(),p.getZ(),1))return false;
      }else if(road.clearanceAt(p))return false;
    }
    return shell;
  }
  private TunnelTerrainSpace(){}
}
