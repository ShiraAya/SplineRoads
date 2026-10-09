package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Narrow-phase dependencies. Distant curves with overlapping bounds are independent. */
final class RoadInteractions {
  static boolean connected(RoadRecord a,RoadRecord b){return a.a().equals(b.a())||a.a().equals(b.b())||a.b().equals(b.a())||a.b().equals(b.b());}
  /** A host edit must derive its lane openings before checking its own connector.
   * The transaction rebuilds/validates these dependents against the final host. */
  static boolean deferredLaneContact(RoadRecord host,RoadRecord ramp){
    var link=LaneTopology.metadata(ramp).link();if(link==null)return false;
    return host.id().equals(link.from().road())||host.id().equals(link.to().road())
        ||host.assembly()!=null&&host.assembly().equals(link.to().junction())
        ||LaneTopology.metadata(host).cuts().stream().anyMatch(c->c.connection().equals(ramp.id()))
        ||LaneTopology.metadata(host).additions().stream().anyMatch(a->a.connection().equals(ramp.id()));
  }
  static boolean influences(RoadIndex.Built a,RoadIndex.Built b){
    if(connected(a.record,b.record))return true;
    double walkway=Math.max(walkExtent(a.mesh),walkExtent(b.mesh));
    if(!RoadIndex.overlapXZ(a.mesh,b.mesh,3+walkway))return false;
    if(LaneTopology.metadata(b.record).link()!=null){
      // A host (or parent-ramp) edit regenerates this dependent in the same
      // transaction. Its old throat cannot veto the new host before that step.
      if(deferredLaneContact(a.record,b.record))return true;
      // A nearby independent ramp is saved authored work. Safe additions keep its
      // furniture; an actual collision must be resolved by the proposed road.
      var link=LaneTopology.metadata(a.record).link();
      var oldLink=LaneTopology.metadata(b.record).link();
      boolean source=link!=null&&(b.record.id().equals(link.from().road())||link.from().equals(oldLink.from())),target=link!=null&&(b.record.id().equals(link.to().road())||link.to().equals(oldLink.to()));
      var host=Map.of(b.record.id(),b.record);var ids=Set.of(b.record.id());
      double begin=source?a.mesh.samples().get(LaneRamps.contactEnd(a.mesh,host,ids,true)).distance():-1;
      double end=target?a.mesh.samples().get(LaneRamps.contactEnd(a.mesh,host,ids,false)).distance():a.mesh.length()+1;
      for(var part:b.record.structures())for(var contact:RoadClearance.structureContacts(part,a.mesh,4.25))if(contact.blocked()
          &&!(source&&contact.to()<=begin+.01||target&&contact.from()>=end-.01))
        throw new IllegalArgumentException(String.format(Locale.ROOT,"新道路与已有匝道设施冲突；冲突道路 %s，构件 %s，坐标 %.2f %.2f %.2f；已保留原匝道",b.record.id(),part.material(),part.a().x(),part.a().y(),part.a().z()));
      // The joined ramp must rebuild its rails against the new paved mouth.
      // Keeping its old furniture after exempting the throat leaves a real fence.
      return source||target;
    }
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
    // A box girder or bearing is part of its OWN slab. Banked/curved sections
    // can weld slightly into that slab; only geometry below every overlapping
    // driving surface qualifies. A lower return leg still blocks the member.
    if(!part.pier()&&part.material()==Material.CONCRETE&&RoadClearance.belowSurface(part,mesh,.025))return false;
    // Use the actual framed prism. A midpoint/bounding-radius test falsely hit
    // nearby lower samples of the SAME sloping deck, removing every ramp pier.
    return RoadClearance.structureInvades(part,mesh,4.25);
  }
  static boolean invades(Part p,Mesh m){
    return RoadClearance.structureInvades(p,m,4.25);
  }
  static List<Part> openPortal(RoadIndex.Built tube,List<Part> parts,List<RoadIndex.Built> roads){
    boolean explicit=tube.record.settings().structure()==Structure.TUNNEL;
    if(!explicit&&(LaneTopology.metadata(tube.record).link()==null||parts.stream().noneMatch(p->p.material()==Material.TUNNEL)))return parts;
    // Lane mouths join a point inside a host, not its physical endpoint block.
    // Open only the contiguous approach to those authored hosts. Later crossings
    // and unrelated roads still reach the normal shell collision validator.
    var link=LaneTopology.metadata(tube.record).link();
    var all=new LinkedHashMap<UUID,RoadRecord>();roads.forEach(r->all.put(r.record.id(),r.record));all.put(tube.record.id(),tube.record);
    var source=link==null?Set.<UUID>of():LaneRamps.contactRoads(all,link.from());
    var target=link==null?Set.<UUID>of():LaneRamps.contactRoads(all,link.to());
    // RoadInfrastructure's lining extends 1.55 beyond the road edge; its last
    // wall segment can still overlap after the deck itself has separated.
    double liningWidth=explicit?1.55:.4;
    var lining=link==null?tube.mesh:RoadRibbon.mesh(tube.mesh.samples().stream().map(s->new Sample(s.center(),s.left(),s.distance(),s.halfWidth()+liningWidth)).toList(),tube.mesh.settings());
    double begin=link==null?0:lining.samples().get(LaneRamps.contactEnd(lining,all,source,true)).distance();
    double finish=link==null?tube.mesh.length():lining.samples().get(LaneRamps.contactEnd(lining,all,target,false)).distance();
    return parts.stream().filter(p->{
      if(p.material()!=Material.TUNNEL&&p.material()!=Material.SIGN_WHITE)return true;
      if(link!=null){
        double station=RoadQueries.horizontal(tube.mesh,p.a().add(p.b()).mul(.5)).sample().distance();
        for(var r:roads)if((source.contains(r.record.id())&&station<=begin||target.contains(r.record.id())&&station>=finish)&&invades(p,r.mesh))return false;
      }
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
