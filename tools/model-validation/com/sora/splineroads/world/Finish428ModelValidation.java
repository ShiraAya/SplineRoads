package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadProfile.*;
import java.util.*;
import net.minecraft.core.BlockPos;
/** Production local continuation pass + topology; NBT/ServerLevel are explicit adapters. */
public final class Finish428ModelValidation {
 static int cases,checks;static void check(boolean ok,String s){checks++;if(!ok)throw new AssertionError(s);}
 static void mergeRemoved(boolean left,boolean twoWay,int sign,boolean viaTopology){
  cases++;var s=Directional427ModelValidation.settings(Type.ORDINARY,3,twoWay?3:0,left);
  var h=Directional427ModelValidation.road(s);var layout=RoadProfile.layout(h.rawMesh(),h.rawMesh().first());
  int slot=-1;double outer=-1;
  for(int i=0;i<RoadLanes.counts(s).total();i++){var l=LanePoints.lane(h.rawMesh(),150,i);if(l.sign()!=sign)continue;double d=Math.abs(l.position().sub(RoadStructures.sample(h.rawMesh(),150).center()).dot(h.rawMesh().first().left())-layout.medianCenter());if(d>outer){slot=i;outer=d;}}
  var p=LanePoints.point(UUID.randomUUID(),LanePoints.Origin.MANUAL,h.rawMesh(),150,slot).merge(24);
  h=h.withLanePoints(LanePoints.Data.EMPTY.points(List.of(p)));var before=new LinkedHashMap<UUID,RoadRecord>();before.put(h.id(),h);LaneCrossSections.reconcile(before);h=before.get(h.id());
  boolean sourceFirst=sign<0;var mesh=h.caps(0).mesh();var end=sourceFirst?mesh.first():mesh.last();
  var section=RoadEndpointSections.section(mesh,sourceFirst,sourceFirst);var settings=RoadEndpointSections.inherit(h.settings(),section);
  V direction=end.left().left().mul(sourceFirst?1:-1);var anchor=RoadMedianAnchor.position(mesh,sourceFirst);var a=new Node(anchor,RoadPlanner.yaw(direction),0);var b=new Node(anchor.add(direction.mul(180)),a.yaw(),0);
  var child=new RoadRecord(UUID.randomUUID(),h.owner(),sourceFirst?h.a():h.b(),new BlockPos(0,8,sourceFirst?-180:480),a,b,settings,true,4);
  before.put(child.id(),child);var all=new LinkedHashMap<>(before);
  all.put(h.id(),h.withLanePoints(LaneTopology.metadata(h).points(viaTopology?List.of():List.of(p.merge(0)))));
  var scope=new HashSet<UUID>();scope.add(h.id());LaneCrossSections.reconcile(all,scope);
  if(viaTopology){var data=new RoadData();for(var old:before.values())data.index.put(new RoadIndex.Built(old));var result=new ArrayList<RoadIndex.Built>();result.add(new RoadIndex.Built(all.get(h.id())));LaneTopology.reconcile(data,result,new HashSet<>());all.clear();for(var built:result)all.put(built.record.id(),built.record);}
  else RoadContinuations.reconcile(before,all,scope);
  var moved=all.get(child.id());check(moved!=null,"changed child absent");var source=all.get(h.id());var expected=sourceFirst?source.caps(0).mesh().first():source.caps(0).mesh().last();
  check(moved.caps(0).mesh().first().center().distance(expected.center())<1e-6,"child physical centre stale");
  check(Math.abs(moved.mesh().first().halfWidth()-expected.halfWidth())<1e-6,"child width stale");
  check(moved.end().equals(child.end()),"far endpoint moved");check(moved.a().equals(child.a())&&moved.b().equals(child.b()),"logical nodes moved");
  check(LaneTopology.metadata(moved).cuts().isEmpty(),"child copied source closure");
  check(RoadRecord.load(moved.save()).mesh().samples().equals(moved.mesh().samples()),"moving connection lost on save");
  var again=new LinkedHashMap<>(all);RoadContinuations.reconcile(before,again,new HashSet<>(scope));check(again.get(child.id()).header().equals(moved.header()),"not idempotent");
  // Undo original merge removal after continuation exists: physically narrow again.
  var undo=new LinkedHashMap<>(all);undo.put(h.id(),source.withLanePoints(LaneTopology.metadata(source).points(List.of(p))));var undoScope=new HashSet<>(Set.of(h.id()));LaneCrossSections.reconcile(undo,undoScope);RoadContinuations.reconcile(all,undo,undoScope);
  check(undo.get(child.id()).mesh().first().center().distance(end.center())<1e-6,"remerge centre stale");
  check(Math.abs(undo.get(child.id()).mesh().first().halfWidth()-end.halfWidth())<1e-6,"remerge width stale");
  check(undo.get(child.id()).end().equals(child.end()),"remerge moved far end "+undo.get(child.id()).end()+" vs "+child.end()+" left="+left+" two="+twoWay+" sign="+sign+" topology="+viaTopology);
 }
 public static void main(String[] args){for(boolean left:new boolean[]{false,true})for(boolean two:new boolean[]{false,true})for(int sign:two?new int[]{-1,1}:new int[]{1})for(boolean full:new boolean[]{false,true})mergeRemoved(left,two,sign,full);System.out.println("Finish428ModelValidation: "+cases+" remove/remerge continuation cases / "+checks+" checks PASS; actual records and local topology, adapters NOT Minecraft transactions");}
}
