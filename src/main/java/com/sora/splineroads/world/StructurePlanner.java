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
    var planting=new HashMap<UUID,Boolean>();
    // Only actual lane connectors opt into the new union-edge policy. Automatic
    // interchange/Y-fork semantics remain unchanged. UUID/depth gives one owner when
    // the rail centrelines coincide; no capsule or distance-only opening can erase them.
    boolean laneEdges=LaneTopology.metadata(built.record).link()!=null||nearby.stream().anyMatch(r->LaneTopology.metadata(r.record).link()!=null);
    var railJoin=laneEdges?new RoadRailJoin(built.mesh,nearby.stream().map(r->new RoadRailJoin.Neighbor(r.mesh,
        RoadSurface.higherPriority(r.record.id(),r.mesh,built.record.id(),built.mesh))).toList()):null;
    var capMouths=RoadRailJoin.mouths(nearby.stream().filter(r->LaneMerge.linkedTo(built.mesh,r.mesh)||LaneMerge.linkedTo(r.mesh,built.mesh)||LaneTopology.metadata(built.record).cuts().stream().anyMatch(c->c.connection().equals(r.record.id()))).map(r->r.mesh).toList());
    var approaches = built.record.assembly() == null ? List.<RoadSignals.Approach>of()
        : RoadSignals.approaches(built.mesh, nearby.stream()
            .filter(r -> built.record.assembly().equals(r.record.assembly()))
            .map(r -> r.mesh).toList());
    var supportMeshes=built.record.junction()==null?List.of(built.mesh):all.stream().filter(r->Objects.equals(r.record.assembly(),built.record.assembly())).map(r->r.mesh).toList();
    var obstacles=lookup.nearAny(supportMeshes,82,built.record.id());
    var motorDecks=new IdentityHashMap<Mesh,Mesh>();
    // Road deck columns exclude smooth sidewalks outside the deck. Index their actual
    // slabs independently so lamp arms/posts cannot tunnel through an upper walkway.
    var sidewalkSolids=new RoadSolidOverlap.Index(obstacles.stream().flatMap(r->r.record.structures().stream())
        .filter(p->p.material().name().startsWith("WALK_")).toList());
    // Piers may stand outside their owning road's deck bounds and record endpoints
    // are not reliable geometric identity. Check their actual local shaft positions.
    var pierSpacing=new RoadPierSpacing(obstacles.stream().flatMap(r->r.record.structures().stream()).toList());
    var terrainReference=built.record.terrainClassificationMesh();
    var terrain=RoadTerrain.read(level,retained,originals,terrainCache);
    var link=LaneTopology.metadata(built.record).link();
    if(link!=null)LaneRampTerrain.validate(built.mesh,link.options(),terrain);
    var ground = new RoadStructures.Ground() {
              public Mesh terrainReference(Mesh mesh){return terrainReference;}
              public boolean closedLanePlanting(UUID connection){
                return planting.computeIfAbsent(connection,id->nearby.stream().filter(r->r.record.id().equals(id)).noneMatch(r->buried(r.mesh,this)));
              }
              public boolean furnitureClear(V point) {
                return LanePoints.opening(built.mesh,point)||RoadSignals.furnitureClear(point, approaches);
              }
              public boolean marker(V point) {
                for (BlockPos p : List.of(built.record.a(), built.record.b()))
                  if (level.getBlockEntity(p) instanceof NodeEntity node
                      && node.node().position().distance(point) < 1.6) return true;
                return false;
              }

              public double top(double x,double z,double deckY){return terrain.top(x,z,deckY);}
              public double surface(double x,double z,double roadY){return terrain.surface(x,z,roadY);}

              public V railJoint(V p,V direction){return railJoin==null?null:railJoin.joint(p,direction);}
              public boolean railPost(V p){return railJoin==null||railJoin.ownsPost(p);}
              public V railJoint(V p,V direction,boolean highway,boolean raised){return railJoin==null?null:railJoin.joint(p,direction,highway,raised);}
              public boolean railPost(V p,boolean highway,boolean raised){return railJoin==null||railJoin.ownsPost(p,highway,raised);}
              public List<RoadRailJoin.Span> railSpans(V a,V b,V outside) {
                return railJoin==null?RoadStructures.Ground.super.railSpans(a,b,outside):railJoin.exposed(a,b,outside);
              }

              public List<RoadRailJoin.Span> railSpans(V a,V b,V outside,double inset){
                return railJoin==null?RoadStructures.Ground.super.railSpans(a,b,outside,inset):railJoin.exposed(a,b,outside,inset);
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

              public String blockedReason(RoadStructures.Part part){
                for(var other:obstacles)if(RoadClearance.structureInvades(part,other.mesh,Math.max(4.25,RoadInfrastructure.clearance(other.record.settings()))))
                  return RoadStructures.Ground.super.blockedReason(part)+"，冲突道路 "+other.record.id();
                return RoadStructures.Ground.super.blockedReason(part);
              }
              public boolean blocked(RoadStructures.Part part) {
                return blocked(part,null,null);
              }
              public boolean railBlocked(RoadStructures.Part part,V a,V b){return blocked(part,a,b);}
              public boolean unionRails(){return laneEdges;}
              private boolean blocked(RoadStructures.Part part,V railA,V railB){
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
                  if(railA!=null&&RoadRailJoin.sharedRail(other.mesh,railA,railB))continue;
                  if(box.maxX<other.mesh.min().x()-2||box.minX>other.mesh.max().x()+2||box.maxZ<other.mesh.min().z()-2||box.minZ>other.mesh.max().z()+2)continue;
                  if (box.maxY < other.mesh.min().y() - .05 || box.minY > other.mesh.max().y() + Math.max(4.25,com.sora.splineroads.core.RoadInfrastructure.clearance(other.record.settings())))
                    continue;
                  if(RoadSidewalks.smoothPart(part)){
                    // Exact clipping after planning keeps the usable part of boundary tiles.
                    continue;
                  }
                  if(part.pier()&&part.material()==RoadStructures.Material.CONCRETE&&RoadSidewalks.blocksTactile(part,other.mesh))return true;
                  if(!part.pier()){
                    if(LaneClosureLandscape.paved(part)&&RoadClearance.belowSurface(part,other.mesh,.025))continue;
                    // The apron/edge slab of a real joining bridge may share the
                    // floor volume. This permits no material above the driving surface.
                    boolean joining=RoadInteractions.connected(built.record,other.record)||LaneMerge.linkedTo(built.mesh,other.mesh)||LaneMerge.linkedTo(other.mesh,built.mesh);
                    if(joining&&part.material()==RoadStructures.Material.CONCRETE&&part.height()<=built.record.settings().thickness()+1e-7
                        &&RoadClearance.belowSurface(part,other.mesh,.025))continue;
                    // Rails may occupy the outside shoulder beside a different
                    // deck height; motor traffic and physical median stay protected.
                    // Testing the full paved verge erased both sides of a raised
                    // junction although neither assembly entered a driving lane.
                    var protectedMesh=railA==null?other.mesh:motorDecks.computeIfAbsent(other.mesh,LaneDeck::motorOnly);
                    if(RoadClearance.structureInvades(part,protectedMesh,Math.max(4.25,RoadInfrastructure.clearance(other.record.settings()))))return true;
                    continue;
                  }
                  if (part.pier() && RoadStructures.fitsMedian(part, other.mesh,
                      nearby.stream().map(r -> r.mesh).toList())) continue;
                  // Use the actual shaft prism and the live deck bands, including
                  // framed/tilted supports, instead of the voxel-column envelope.
                  if(RoadClearance.structureInvades(part,other.mesh,Math.max(4.25,RoadInfrastructure.clearance(other.record.settings()))))return true;
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

  /** Read the same original terrain as tunnel planning. A road may be buried;
   * this only suppresses its reserved lane's planter and concrete lid. */
  static boolean buried(Mesh mesh,RoadStructures.Ground ground){
    if(mesh.settings().structure()==Structure.TUNNEL)return true;
    for(double d=0;d<mesh.length();d+=1){
      var sample=RoadStructures.sample(mesh,Math.min(mesh.length(),d+.5));int covered=0;
      for(double side:new double[]{-.7,0,.7}){
        V p=sample.at(side*sample.halfWidth(),0);double top=ground.top(p.x(),p.z(),p.y()+6);
        if(Double.isFinite(top)&&top>p.y()+.12)covered++;
      }
      if(covered>=2)return true;
    }return false;
  }
  private StructurePlanner() {}
}
