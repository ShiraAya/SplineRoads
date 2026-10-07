package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Narrow-phase dependencies. Distant curves with overlapping bounds are independent. */
final class RoadInteractions {
  static boolean connected(RoadRecord a,RoadRecord b){return a.a().equals(b.a())||a.a().equals(b.b())||a.b().equals(b.a())||a.b().equals(b.b());}
  static boolean influences(RoadIndex.Built a,RoadIndex.Built b){
    if(connected(a.record,b.record))return true;
    double walkway=Math.max(walkExtent(a.mesh),walkExtent(b.mesh));
    if(!RoadIndex.overlapXZ(a.mesh,b.mesh,3+walkway))return false;
    // Any existing elevated road depends on a new lower corridor, including
    // saved ramp decks. Rebuild its supports and the lower road's lamps.
    // Same-height independent neighbours keep their saved furniture.
    for(var s:b.mesh.samples()){
      var q=RoadQueries.horizontal(a.mesh,s.center());double dy=s.center().y()-q.sample().center().y();
      if(dy>3&&dy<RoadStructures.MAX_DROP&&s.center().sub(q.sample().center()).horizontalLength()<s.halfWidth()+q.sample().halfWidth()+1+walkway)return true;
      if(dy<-.25&&dy>-10
          &&s.center().sub(q.sample().center()).horizontalLength()<s.halfWidth()+q.sample().halfWidth()+3+walkway)return true;
    }
    return false;
  }
  private static double walkExtent(Mesh mesh){
    var walk=mesh.settings().options().sidewalk();
    return walk.enabled()&&walk.smooth()&&mesh.settings().structure()!=Structure.TUNNEL&&RoadProfile.catalog(mesh.settings().style()).type()==RoadProfile.Type.ORDINARY?walk.width():0;
  }
  private static boolean near(Mesh a,Mesh b){
    for(var s:a.samples()){
      var p=RoadQueries.horizontal(b,s.center());
      double dx=s.center().sub(p.sample().center()).horizontalLength();
      if(dx<s.halfWidth()+p.sample().halfWidth()+3 && Math.abs(s.center().y()-p.sample().center().y())<RoadStructures.MAX_DROP)return true;
    }return false;
  }
  static boolean selfSupportBlocked(Part part,Mesh mesh){
    if(!part.pier()&&(part.material()!=Material.CONCRETE||part.width()<1||part.height()<.5))return false;
    // Use the actual framed prism. A midpoint/bounding-radius test falsely hit
    // nearby lower samples of the SAME sloping deck, removing every ramp pier.
    return RoadClearance.structureInvades(part,mesh,4.25);
  }
  static boolean invades(Part p,Mesh m){
    return RoadClearance.structureInvades(p,m,4.25);
  }
  static List<Part> openPortal(RoadIndex.Built tube,List<Part> parts,List<RoadIndex.Built> roads){
    if(tube.record.settings().structure()!=Structure.TUNNEL)return parts;
    return parts.stream().filter(p->{
      if(p.material()!=Material.TUNNEL&&p.material()!=Material.SIGN_WHITE)return true;
      for(var r:roads)if(!r.record.id().equals(tube.record.id())&&connected(tube.record,r.record)){
        V mid=p.a().add(p.b()).mul(.5);
        for(var pos:List.of(tube.record.a(),tube.record.b()))if(pos.equals(r.record.a())||pos.equals(r.record.b())){
          V end=pos.equals(tube.record.a())?tube.mesh.first().center():tube.mesh.last().center();
          if(mid.sub(end).horizontalLength()<Math.max(8,(tube.record.settings().width()+r.record.settings().width())*.6+3)&&invades(p,r.mesh))return false;
        }
      }return true;
    }).toList();
  }
}
