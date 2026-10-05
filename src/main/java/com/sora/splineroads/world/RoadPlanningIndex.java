package com.sora.splineroads.world;

import com.sora.splineroads.core.RoadBoundsIndex;
import com.sora.splineroads.core.RoadGeometry.Mesh;
import java.util.*;

/** One mutable planning snapshot for an edit transaction, never retained by the world.
 * Replacing a planned road updates geometry AND structures seen by subsequent roads. */
final class RoadPlanningIndex {
  private final List<RoadIndex.Built> roads;
  private final RoadBoundsIndex bounds;
  RoadPlanningIndex(List<RoadIndex.Built> roads){this.roads=new ArrayList<>(roads);bounds=new RoadBoundsIndex(roads.stream().map(r->box(r.mesh)).toList());}
  private static RoadBoundsIndex.Bounds box(Mesh m){return new RoadBoundsIndex.Bounds(m.min().x(),m.min().z(),m.max().x(),m.max().z());}
  void replace(int slot,RoadIndex.Built next){bounds.replace(slot,box(next.mesh));roads.set(slot,next);}
  List<RoadIndex.Built> near(Mesh mesh,double margin,UUID exclude){
    var result=new ArrayList<RoadIndex.Built>();for(int slot:bounds.query(box(mesh),margin)){var r=roads.get(slot);if(!r.record.id().equals(exclude))result.add(r);}return result;
  }
  List<RoadIndex.Built> nearAny(List<Mesh> meshes,double margin,UUID exclude){
    var slots=new TreeSet<Integer>();for(var mesh:meshes)slots.addAll(bounds.query(box(mesh),margin));
    var result=new ArrayList<RoadIndex.Built>();for(int slot:slots){var r=roads.get(slot);if(!r.record.id().equals(exclude))result.add(r);}return result;
  }
}
