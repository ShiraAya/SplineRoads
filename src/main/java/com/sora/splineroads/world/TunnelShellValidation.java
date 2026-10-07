package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Validate new shell/travel conflicts; unchanged saved conflicts do not veto a deletion.
 * This does NOT disable checks while deleting: new end collars and changed neighboring
 * decks are still checked. Old part equality and actual travel geometry must BOTH match. */
final class TunnelShellValidation {
  static void check(List<RoadIndex.Built> planned,Set<UUID> changed,Map<UUID,RoadIndex.Built> previous){
    for(var tube:planned){
      if(!tube.hasTunnel())continue;
      var oldTube=previous.get(tube.record.id());
      Set<RoadStructures.Part> saved=oldTube==null?Set.of():new HashSet<>(oldTube.record.structures());
      for(var other:planned){
        if(other.record.id().equals(tube.record.id())||(!changed.contains(tube.record.id())&&!changed.contains(other.record.id()))
            ||!RoadIndex.overlapXZ(tube.mesh,other.mesh,2))continue;
        var oldOther=previous.get(other.record.id());
        boolean unchangedTravel=oldOther!=null&&sameTravel(oldOther.mesh,other.mesh);
        for(var part:tube.record.structures()){
          if(part.material()!=RoadStructures.Material.TUNNEL)continue;
          if(unchangedTravel&&saved.contains(part))continue;
          if(RoadInteractions.invades(part,other.mesh))throw new IllegalArgumentException(
              "隧道墙顶侵入另一条道路的通行空间：隧道 "+tube.record.id()+"，道路 "+other.record.id()+
              String.format(Locale.ROOT,"，结构位置 %.2f %.2f %.2f；请调整高度或走线",part.a().x(),part.a().y(),part.a().z()));
        }
      }
    }
  }
  private static boolean sameTravel(Mesh a,Mesh b){
    return a==b||a.settings().thickness()==b.settings().thickness()&&a.samples().equals(b.samples())
        &&a.settings().options().lanePoints().cuts().equals(b.settings().options().lanePoints().cuts());
  }
  private TunnelShellValidation(){}
}
