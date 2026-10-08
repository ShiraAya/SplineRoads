package com.sora.splineroads.world;

import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

final class StructurePlanner {
  static RoadRecord plan(
      ServerLevel level,
      RoadIndex.Built built,
      List<RoadIndex.Built> all,
      Map<Long, BlockState> originals,
      Map<BlockPos, List<AABB>> terrainCache) {
    return plan(level,built,all,originals,terrainCache,new RoadPlanningIndex(all));
  }

  static RoadRecord plan(ServerLevel level,RoadIndex.Built built,List<RoadIndex.Built> all,
      Map<Long,BlockState> originals,Map<BlockPos,List<AABB>> terrainCache,RoadPlanningIndex lookup) {
    return plan(level,built,all,originals,Map.of(),terrainCache,lookup);
  }

  static RoadRecord plan(ServerLevel level,RoadIndex.Built built,List<RoadIndex.Built> all,
      Map<Long,BlockState> retained,Map<Long,BlockState> originals,
      Map<BlockPos,List<AABB>> terrainCache,RoadPlanningIndex lookup) {
    var nearby=lookup.near(built.mesh,3,built.record.id());
    // Only actual lane connectors opt into the new union-edge policy. Automatic
    // interchange/Y-fork semantics remain unchanged. UUID/depth gives one owner when
    // the rail centrelines coincide; no capsule or distance-only opening can erase them.
    boolean laneEdges=LaneTopology.metadata(built.record).link()!=null||nearby.stream().anyMatch(r->LaneTopology.metadata(r.record).link()!=null);
    var railJoin=laneEdges?new RoadRailJoin(nearby.stream().map(r->new RoadRailJoin.Neighbor(r.mesh,
        RoadSurface.higherPriority(r.record.id(),r.mesh,built.record.id(),built.mesh))).toList()):null;
    var capMouths=RoadRailJoin.mouths(nearby.stream().filter(r->LaneMerge.linkedTo(built.mesh,r.mesh)||LaneMerge.linkedTo(r.mesh,built.mesh)||LaneTopology.metadata(built.record).cuts().stream().anyMatch(c->c.connection().equals(r.record.id()))).map(r->r.mesh).toList());
    var approaches = built.record.assembly() == null ? List.<RoadSignals.Approach>of()
        : RoadSignals.approaches(built.mesh, nearby.stream()
            .filter(r -> built.record.assembly().equals(r.record.assembly()))
            .map(r -> r.mesh).toList());
    var supportMeshes=built.record.junction()==null?List.of(built.mesh):all.stream().filter(r->Objects.equals(r.record.assembly(),built.record.assembly())).map(r->r.mesh).toList();
    var obstacles=lookup.nearAny(supportMeshes,82,built.record.id());
    // Road deck columns exclude smooth sidewalks outside the deck. Index their actual
    // slabs independently so lamp arms/posts cannot tunnel through an upper walkway.
    var sidewalkSolids=new RoadSolidOverlap.Index(obstacles.stream().flatMap(r->r.record.structures().stream())
        .filter(p->p.material().name().startsWith("WALK_")).toList());
    // Piers may stand outside their owning road's deck bounds and record endpoints
    // are not reliable geometric identity. Check their actual local shaft positions.
    var pierSpacing=new RoadPierSpacing(obstacles.stream().flatMap(r->r.record.structures().stream()).toList());
    var ground = new RoadStructures.Ground() {
              public boolean furnitureClear(V point) {
                return LanePoints.opening(built.mesh,point)||RoadSignals.furnitureClear(point, approaches);
              }
              public boolean marker(V point) {
                for (BlockPos p : List.of(built.record.a(), built.record.b()))
                  if (level.getBlockEntity(p) instanceof NodeEntity node
                      && node.node().position().distance(point) < 1.6) return true;
                return false;
              }

              public double top(double x, double z, double deckY) {
                BlockPos key = BlockPos.containing(x, deckY, z);
                if(!RoadWorkChunks.terrainAvailable(level,key))return Double.NaN;
                var surfaces =
                    terrainCache.computeIfAbsent(
                        key,
                        k -> {
                          List<AABB> out = new ArrayList<>();
                          int bottom =
                              Math.max(
                                  level.getMinBuildHeight(),
                                  key.getY() - (int) RoadStructures.MAX_DROP);
                          for (int y = key.getY(); y >= bottom; y--) {
                            BlockPos p = new BlockPos(key.getX(), y, key.getZ());
                            if (!level.hasChunkAt(p)) break;
                            BlockState state = level.getBlockState(p);
                            state = RoadFoundation.source(p.asLong(),state,
                                RoadBlocks.isCollider(state)||state.is(SplineRoads.TUNNEL_AIR.get()),
                                state.isAir(),originals,retained,
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                            if (state.is(net.minecraft.tags.BlockTags.LEAVES)
                                || state.is(net.minecraft.tags.BlockTags.LOGS)
                                || state.is(SplineRoads.NODE.get())
                                || !state.getFluidState().isEmpty()) continue;
                            var shape=state.getCollisionShape(level,p);
                            for (var box : shape.toAabbs())
                              out.add(box.move(p));
                            // A full cube hides all lower surfaces in this column for every x/z
                            // query.
                            if (y < key.getY()
                                && net.minecraft.world.level.block.Block.isShapeFullBlock(
                                    shape)) break;
                          }
                          out.sort(Comparator.comparingDouble((AABB box) -> box.maxY).reversed());
                          return out;
                        });
                for (var box : surfaces)
                  if (x >= box.minX - 1e-7
                      && x <= box.maxX + 1e-7
                      && z >= box.minZ - 1e-7
                      && z <= box.maxZ + 1e-7
                      // A block beginning at the probe plane is overhead, not a foundation.
                      && box.minY < deckY - 1e-7) return Math.min(box.maxY,deckY);
                return Double.NaN;
              }

              public V railJoint(V p,V direction){return railJoin==null?null:railJoin.joint(p,direction);}
              public boolean railPost(V p){return railJoin==null||railJoin.ownsPost(p);}
              public V railJoint(V p,V direction,boolean highway,boolean raised){return railJoin==null?null:railJoin.joint(p,direction,highway,raised);}
              public boolean railPost(V p,boolean highway,boolean raised){return railJoin==null||railJoin.ownsPost(p,highway,raised);}
              public List<RoadRailJoin.Span> railSpans(V a,V b,V outside) {
                return railJoin==null?RoadStructures.Ground.super.railSpans(a,b,outside):railJoin.exposed(a,b,outside);
              }

              public List<RoadRailJoin.Span> capRailSpans(V a,V b,V outside){
                var result=new ArrayList<RoadRailJoin.Span>();
                for(var span:railSpans(a,b,outside))result.addAll(capMouths.exposedMouth(span.a(),span.b()));
                return result;
              }
              public boolean joined(V point) {
                if(LanePoints.opening(built.mesh,point))return true;
                for (var other : nearby)
                  if (RoadQueries.joins(built.mesh, other.mesh, point)) return true;
                return false;
              }

              public boolean blocked(RoadStructures.Part part) {
                if(pierSpacing.tooClose(part))return true;
                if(!RoadSidewalks.smoothPart(part)&&sidewalkSolids.intersects(part))return true;
                if(LaneTopology.metadata(built.record).link()!=null&&RoadInteractions.selfSupportBlocked(part,built.mesh))return true;
                // Rails use the exact deck opening test above. The pier's vehicle-clearance
                // envelope would otherwise erase rails for several blocks around every seam.
                if (!laneEdges && !part.pier() && part.material() == RoadStructures.Material.DEFAULT)
                  return false;
                double w = part.halfExtent() + (part.pier() ? .5 : .05);
                AABB box =
                    new AABB(
                        Math.min(part.a().x(), part.b().x()) - w,
                        Math.min(part.a().y(), part.b().y())-part.verticalFrame(),
                        Math.min(part.a().z(), part.b().z()) - w,
                        Math.max(part.a().x(), part.b().x()) + w,
                        Math.max(part.a().y(), part.b().y()) + part.height()+part.verticalFrame(),
                        Math.max(part.a().z(), part.b().z()) + w);
                boolean precise=true;
                if(precise&&!part.pier()){
                  var base=part.base();
                  box=new AABB(base.stream().mapToDouble(V::x).min().orElseThrow(),base.stream().mapToDouble(V::y).min().orElseThrow(),base.stream().mapToDouble(V::z).min().orElseThrow(),
                      base.stream().mapToDouble(V::x).max().orElseThrow(),base.stream().mapToDouble(V::y).max().orElseThrow()+part.height(),base.stream().mapToDouble(V::z).max().orElseThrow());
                }
                for (var other : obstacles) {
                  if(box.maxX<other.mesh.min().x()-2||box.minX>other.mesh.max().x()+2||box.maxZ<other.mesh.min().z()-2||box.minZ>other.mesh.max().z()+2)continue;
                  if (box.maxY < other.mesh.min().y() - .05 || box.minY > other.mesh.max().y() + Math.max(4.25,com.sora.splineroads.core.RoadInfrastructure.clearance(other.record.settings())))
                    continue;
                  if(RoadSidewalks.smoothPart(part)){
                    // Exact clipping after planning keeps the usable part of boundary tiles.
                    continue;
                  }
                  if(part.pier()&&part.material()==RoadStructures.Material.CONCRETE&&RoadSidewalks.blocksTactile(part,other.mesh))return true;
                  if(!part.pier()){
                    if(RoadClearance.structureInvades(part,other.mesh,Math.max(4.25,RoadInfrastructure.clearance(other.record.settings()))))return true;
                    continue;
                  }
                  if (part.pier() && RoadStructures.fitsMedian(part, other.mesh,
                      nearby.stream().map(r -> r.mesh).toList())) continue;
                  // Voxel columns are a broad phase, not proof that a shaft occupies a lane.
                  if(!RoadSidewalks.overlapsDeck(part,other.mesh))continue;
                  for (int x = (int) Math.floor(box.minX); x < Math.ceil(box.maxX); x++)
                    for (int z = (int) Math.floor(box.minZ); z < Math.ceil(box.maxZ); z++) {
                      var col = other.column(new BlockPos(x, 0, z));
                      boolean sharedEnd=built.record.a().equals(other.record.a())||built.record.a().equals(other.record.b())
                          ||built.record.b().equals(other.record.a())||built.record.b().equals(other.record.b());
                      // The new edge slab may touch the deck of its connected continuation.
                      // It stays at/below that road's surface and cannot occupy its vehicle clearance.
                      if(col!=null&&sharedEnd&&part.material()==RoadStructures.Material.CONCRETE&&!part.pier()
                          &&part.height()<=built.record.settings().thickness()+1e-7&&box.maxY<=col.minTop()+.025)continue;
                      if (col != null
                          && box.maxY > col.minTop() - other.record.settings().thickness() + (precise?.02:-.1)
                          && box.minY < col.maxTop() + Math.max(4.25,com.sora.splineroads.core.RoadInfrastructure.clearance(other.record.settings()))) return true;
                    }
                }
                return false;
              }
            };
    if(built.record.junction()!=null){
      var ref=built.record.junction();var parts=new ArrayList<>(ref.get().structures());
      if(ref.get().arm()>=0){
        var arm=ref.spec().arms().get(ref.get().arm());var setting=JunctionRoads.editable(arm);
        var source=built.mesh;var proxy=new Mesh(source.samples(),setting,source.min(),source.max(),source.length(),source.closed());
        var decoratedGround=new RoadStructures.Ground(){
          public double top(double x,double z,double y){return ground.top(x,z,y);}
          public boolean joined(V p){return ground.joined(p);}
          public boolean blocked(RoadStructures.Part p){
            if(ground.blocked(p))return true;
            for(var old:parts)if(old.material()==RoadStructures.Material.CB_BASE||old.material()==RoadStructures.Material.CB_POST||old.material()==RoadStructures.Material.CB_ARM){
              if(p.a().y()+p.height()<=old.a().y()||old.a().y()+old.height()<=p.a().y())continue;
              if(RoadSurface.area(p.base())-RoadSurface.subtract(p.base(),old.base()).stream().mapToDouble(RoadSurface::area).sum()>1e-6)return true;
            }return false;
          }
          public boolean furnitureClear(V p){double distance=RoadQueries.horizontal(proxy,p).sample().distance();double setback=arm.crossingSetback()+(arm.crosswalk()?arm.crossingWidth():0)+arm.stopGap()+3;return distance>proxy.length()-setback||ground.furnitureClear(p);}
        };
        var amenities=RoadStreetscape.plan(proxy,decoratedGround,FurnitureSpacing.onMesh(built.record,proxy));
        parts.addAll(amenities);
      }
      if(ref.get().arm()>=0||ref.spec().kind()==JunctionSpec.Kind.ROUNDABOUT)
        parts.addAll(RoadStructures.supports(built.mesh,ground,FurnitureSpacing.onMesh(built.record,built.mesh)));
      else if(ref.piece()==ref.spec().arms().size()){
        // One owner for the entire central pad, not one column per fill triangle.
        var edge=ref.boundary();V center=ref.spec().center();
        double minX=edge.stream().mapToDouble(V::x).min().orElse(center.x()),maxX=edge.stream().mapToDouble(V::x).max().orElse(center.x());
        double minZ=edge.stream().mapToDouble(V::z).min().orElse(center.z()),maxZ=edge.stream().mapToDouble(V::z).max().orElse(center.z());
        for(double x=center.x()+Math.ceil((minX-center.x())/24)*24;x<=maxX;x+=24)for(double z=center.z()+Math.ceil((minZ-center.z())/24)*24;z<=maxZ;z+=24){
          V at=new V(x,center.y(),z);boolean inside=true;
          for(double dx:new double[]{-2,2})for(double dz:new double[]{-2,2})inside&=JunctionPaint.inside(edge,at.add(new V(dx,0,dz)));
          if(inside)parts.addAll(RoadSupports.clearStandard(new Sample(at,new V(1,0,0),0,2),ref.spec().thickness(),ground));
        }
      }
      return built.record.structures(parts);
    }
    var parts=RoadStructures.plan(built.mesh,ground,FurnitureSpacing.onMesh(built.record,built.mesh));
    if(built.record.assembly()!=null){
      var siblings=all.stream().filter(r->built.record.assembly().equals(r.record.assembly())).toList();
      if(siblings.size()==3){
        var stem=siblings.stream().filter(r->RoadProfile.catalog(r.record.settings().style()).twoWay()&&!r.record.settings().options().ends().persistent()).findFirst().orElse(null);
        if(stem!=null){
          var out=siblings.stream().filter(r->r!=stem&&r.record.a().equals(stem.record.b())&&r.record.settings().options().ends().persistent()).findFirst().orElse(null);
          var in=siblings.stream().filter(r->r!=stem&&r.record.b().equals(stem.record.b())&&r.record.settings().options().ends().persistent()).findFirst().orElse(null);
          if(out!=null&&in!=null)parts=YJunctionWalks.apply(built.mesh,stem.mesh,out.mesh,in.mesh,parts);
        }
      }
    }
    var grading=new ArrayList<>(parts);nearby.forEach(r->grading.addAll(r.record.structures()));
    parts=RoadAutoTunnels.enclose(built.mesh,ground,parts);
    parts=new TactileSurface(grading).gradePaving(parts);
    var joins=new SidewalkJoins(nearby.stream().filter(r->Math.abs(r.mesh.first().center().y()-built.mesh.first().center().y())<4).map(r->r.mesh).toList(),nearby.stream().flatMap(r->r.record.structures().stream()).filter(RoadSidewalks::smoothPart).toList());
    var result = new ArrayList<>(joins.clip(RoadInteractions.openPortal(built,parts,nearby)));
    var paving=new ArrayList<>(result);nearby.forEach(r->paving.addAll(r.record.structures()));
    result=new ArrayList<>(new TactileSurface(paving).conform(result));
    // Only assembly junction pads control a road; an unrelated unmarked deck is not a signal.
    result.addAll(RoadSignals.structures(approaches));
    return built.record.derivedStreetscape(built.record.settings().options().streetscape().raisedSpans(RoadStreetscape.classify(built.mesh,ground))).structures(result);
  }

  private StructurePlanner() {}
}
