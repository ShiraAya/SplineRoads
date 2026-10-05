package com.sora.splineroads.world;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_endpoint_v2") @PrefixGameTestTemplate(false)
public final class EndpointV2GameTests {
 static Settings road(Style s){return Revision32GameTests.road(s,Structure.GROUND);}
 static RoadAttachments.Point add(RoadData data,net.minecraft.server.level.ServerLevel level,RoadRecord road,double x,double y,double z){V v=new V(x,RoadQueries.horizontal(road.mesh(),new V(x,y,z)).sample().center().y(),z);var p=new RoadAttachments.Point(UUID.randomUUID(),v,v,false);var d=road.settings().options().attachments();var points=new ArrayList<>(d.points());points.add(p);data.updatePointMetadata(level,null,road,d.points(points));return p;}
 @GameTest(template="empty",templateNamespace="splineroads_endpoint_v2",timeoutTicks=18000)
 public static void terrainRestoresAndOrphanBorderIsRemoved(GameTestHelper h){
  var l=h.getLevel();var data=RoadData.get(l);int x=150000,z=150000;
  for(int xx=x-10;xx<x+70;xx++)for(int zz=z-20;zz<z+50;zz++){var p=new BlockPos(xx,1,zz);l.getChunkAt(p);l.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);}
  var a=Revision32GameTests.marker(h,x,2,z,-70);var b=Revision32GameTests.marker(h,x+60,2,z+20,-70);var s=road(Style.O2_YELLOW);s=s.options(s.options().sidewalk(new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,4,"minecraft:stone_bricks",true,true)));
  var obstacle=new BlockPos(x+30,4,z+10);l.setBlock(obstacle,Blocks.OAK_LOG.defaultBlockState(),2);
  var r=data.connect(l,null,a,b,s,null);h.assertTrue(l.getBlockState(obstacle).isAir(),"headroom obstacle cleared");
  var saved=RoadData.load(data.save(new CompoundTag()));h.assertTrue(saved.index.roads.containsKey(r.id()),"saved road exists");
  data.remove(l,null,r.id());for(int xx=x+2;xx<x+58;xx++)for(int zz=z-15;zz<z+35;zz++)h.assertTrue(l.getBlockState(new BlockPos(xx,1,zz)).is(Blocks.GRASS_BLOCK),"original diagonal sidewalk ground restored at "+xx+","+zz);
  h.assertTrue(l.getBlockState(obstacle).isAir(),"headroom obstacle is not restored");h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_endpoint_v2",timeoutTicks=18000)
 public static void multiplePointsShapePaintReloadAndRollback(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);int x=152000,z=152000;var a=Revision32GameTests.marker(h,x,200,z,-90);var b=Revision32GameTests.marker(h,x+100,200,z,-90);var s=RoadPlanner.mode(road(Style.O4_YELLOW),Mode.CURVE,90);var r=d.connect(l,null,a,b,s,null);
  var p=add(d,l,r,x+30.5,200,z+.5);r=d.index.roads.get(r.id()).record;var q=add(d,l,r,x+70.5,200,z+.5);r=d.index.roads.get(r.id()).record;
  h.assertTrue(d.index.atNode(a).size()==1&&d.index.atNode(b).size()==1,"creating points does not split road");
  var edit=new CompoundTag();edit.putUUID("Id",r.id());edit.putUUID("Point",p.id());edit.putInt("Signature",GantryTool.signature(r));edit.put("Position",RoadRecord.writeNode(new Node(new V(x+30.5,201,z+3.5),0,0)));AttachedPointTool.edit(l,null,edit);
  var built=d.index.roads.get(r.id());h.assertTrue(built.mesh.samples().stream().anyMatch(v->v.center().distance(new V(x+30.5,201,z+3.5))<1e-6),"curve goes through edited point");
  h.assertTrue(built.record.settings().options().attachments().points().stream().anyMatch(v->v.id().equals(q.id())),"second point retained");
  var line=new CompoundTag();line.putUUID("Id",r.id());line.putInt("Signature",GantryTool.signature(built.record));line.putDouble("Station",50);line.putBoolean("HideArrows",true);d.editLaneLine(l,null,line);built=d.index.roads.get(r.id());
  h.assertTrue(!RoadAttachments.paint(built.mesh,10).hideArrows()&&RoadAttachments.paint(built.mesh,50).hideArrows()&&!RoadAttachments.paint(built.mesh,90).hideArrows(),"paint isolated to clicked interval");
  var loaded=RoadData.load(d.save(new CompoundTag())).index.roads.get(r.id());h.assertTrue(loaded.record.settings().options().attachments().equals(built.record.settings().options().attachments()),"points and paint survive NBT reload");
  var before=d.save(new CompoundTag());edit.putInt("Signature",GantryTool.signature(built.record));edit.putBoolean("Delete",true);boolean denied=false;try{AttachedPointTool.edit(l,null,edit);}catch(IllegalArgumentException e){denied=true;}h.assertTrue(denied&&before.equals(d.save(new CompoundTag())),"deleting boundary with different paint rejects atomically");
  h.assertTrue(SplineRoads.RAMP_CONNECTOR.get() instanceof LaneRampTool,"lane-point connector replaces phase-one placeholder");h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_endpoint_v2",timeoutTicks=24000)
 public static void continuousRangeActuallyBuildsAndUnlocks(GameTestHelper h){
  var l=h.getLevel();var data=RoadData.get(l);int x=155000,z=155000;var a=Revision32GameTests.marker(h,x-180,180,z,-90);var b=Revision32GameTests.marker(h,x+20,180,z,-90);var c=Revision32GameTests.marker(h,x+120,180,z,-90);var u=Revision32GameTests.marker(h,x,187,z-180,0);var v=Revision32GameTests.marker(h,x,187,z+180,0);
  var ab=data.connect(l,null,a,b,road(Style.H4_RAIL),null);var bc=data.connect(l,null,b,c,road(Style.H4_YELLOW),null);var uv=data.connect(l,null,u,v,road(Style.H4_RAIL),null);
  var p=add(data,l,bc,x+80.5,180,z+.5);long[] points={u.asLong(),v.asLong(),a.asLong(),b.asLong()};var payload=Interchanges.payload(l,null,points,null);payload.put("Options",Interchanges.write(new Options(Preset.DIAMOND,false,1,48,12,5,1,5,false,false,0)));
  var plan=Interchanges.plan(payload);h.assertTrue(plan.legs().stream().anyMatch(leg->bc.id().equals(ContinuousRoads.sourceId(leg.name()))),"preview includes actually used adjacent road");h.assertTrue(plan.anchors().get(3).position().x()>x+20.5&&plan.anchors().get(3).position().x()<x+120.5,"only required part of BC is occupied");
  Node before=((NodeEntity)l.getBlockEntity(b)).node();Interchanges.build(l,null,payload);h.assertTrue(before.equals(((NodeEntity)l.getBlockEntity(b)).node()),"auto adjustment off keeps real B unchanged");
  var next=data.index.roads.get(bc.id());h.assertTrue(next!=null&&next.record.assembly()!=null,"BC identity retained and actual occupied road locked");h.assertTrue(next.record.a().equals(b)&&next.record.b().equals(c),"BC actual segment retained");
  h.assertTrue(next.record.settings().style()==Style.H4_YELLOW,"BC retains own style");h.assertTrue(next.record.settings().options().attachments().points().stream().anyMatch(q->q.id().equals(p.id())),"BC point identity preserved");
  var illegal=new CompoundTag();illegal.putUUID("Id",bc.id());illegal.putUUID("Point",p.id());illegal.putInt("Signature",GantryTool.signature(next.record));illegal.put("Position",RoadRecord.writeNode(new Node(p.position().add(new V(0,1,0)),0,0)));boolean shapeBlocked=false;try{AttachedPointTool.edit(l,null,illegal);}catch(IllegalArgumentException e){shapeBlocked=true;}h.assertTrue(shapeBlocked,"assembly main point cannot shape road");
  UUID group=next.record.assembly();var loaded=RoadData.load(data.save(new CompoundTag()));h.assertTrue(loaded.index.roads.get(bc.id()).record.assembly().equals(group),"occupancy survives reload");
  var oldWidth=next.mesh.samples().stream().mapToDouble(Sample::halfWidth).max().orElseThrow();
  var again=Interchanges.payload(l,null,points,group);Interchanges.build(l,null,again);h.assertTrue(Math.abs(oldWidth-data.index.roads.get(bc.id()).mesh.samples().stream().mapToDouble(Sample::halfWidth).max().orElseThrow())<1e-6,"rebuilding does not accumulate auxiliary width");
  boolean locked=false;try{data.connect(l,null,b,c,road(Style.H4_YELLOW),bc.id());}catch(IllegalArgumentException e){locked=true;}h.assertTrue(locked,"road editor cannot modify occupied BC");
  Interchanges.removeRamps(l,null,group);h.assertTrue(data.index.roads.get(bc.id()).record.assembly()==null,"ramp deletion unlocks BC");h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_endpoint_v2",timeoutTicks=18000)
 public static void shared100Plus100MovesBothWaysWithIdentity(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);int x=158000,z=158000;var a=Revision32GameTests.marker(h,x,200,z,-90);var b=Revision32GameTests.marker(h,x+100,200,z,-90);var c=Revision32GameTests.marker(h,x+200,200,z,-90);
  var ab=d.connect(l,null,a,b,road(Style.H4_RAIL),null);var bc=d.connect(l,null,b,c,road(Style.H4_YELLOW),null);var p=add(d,l,bc,x+125.5,200,z+.5);
  var paint=new CompoundTag();paint.putUUID("Id",bc.id());paint.putInt("Signature",GantryTool.signature(d.index.roads.get(bc.id()).record));paint.putDouble("Station",10);paint.putBoolean("HideArrows",true);d.editLaneLine(l,null,paint);
  for(int offset:new int[]{150,100}){
    var marker=RoadData.requireNode(l,b,null);Node target=new Node(new V(x+offset+.5,marker.node().position().y(),z+.5),-90,0);var records=new ArrayList<RoadIndex.Built>();Set<UUID> removed=new HashSet<>();SharedRoadEndpoints.stage(d,l,null,b,target,records,removed);BlockPos to=BlockPos.containing(target.position().x(),target.position().y(),target.position().z());
    d.replaceAssembly(l,null,records,removed,Set.of(a,c),List.of(new RoadData.NodeMove(b,to,target,marker.saveWithoutMetadata())));b=to;
    h.assertTrue(Math.abs(d.index.roads.get(ab.id()).record.end().position().sub(d.index.roads.get(ab.id()).record.start().position()).horizontalLength()-offset)<.01&&Math.abs(d.index.roads.get(bc.id()).record.end().position().sub(d.index.roads.get(bc.id()).record.start().position()).horizontalLength()-(200-offset))<.01,"both road lengths change together");
    var owner=d.index.roads.values().stream().filter(r->r.record.settings().options().attachments().points().stream().anyMatch(q->q.id().equals(p.id()))).findFirst().orElseThrow();
    h.assertTrue(owner.record.id().equals(offset==150?ab.id():bc.id()),"point migrates both directions retaining ID");var after=owner.record.settings().options().attachments().points().stream().filter(q->q.id().equals(p.id())).findFirst().orElseThrow();h.assertTrue(after.position().equals(p.position()),"point XYZ remains fixed on level roads");
    for(double location:new double[]{90,112,140,175}){var rb=d.index.roads.get(location<offset?ab.id():bc.id());boolean hidden=RoadAttachments.paint(rb.mesh,RoadAttachments.station(rb.mesh,new V(x+location+.5,200,z+.5))).hideArrows();h.assertTrue(hidden==(location>100&&location<125),"world-space paint preserved after point migration at "+location);}
    h.assertTrue(d.index.roads.get(ab.id()).record.settings().style()==Style.H4_RAIL&&d.index.roads.get(bc.id()).record.settings().style()==Style.H4_YELLOW,"distinct road styles preserved");
  }h.succeed();
 }

 @GameTest(template="empty",templateNamespace="splineroads_endpoint_v2",timeoutTicks=18000)
 public static void deletingControlRestoresRoadAndPlacementOrigin(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);int x=160000,z=160000;
  for(var mode:List.of(Mode.STRAIGHT,Mode.CURVE)){
   var aa=Revision32GameTests.marker(h,x,210,z,-90);var bb=Revision32GameTests.marker(h,x+120,210,z,-90);var r=d.connect(l,null,aa,bb,RoadPlanner.mode(road(Style.O2_YELLOW),mode,90),null);var base=r.mesh();
   var oldA=new RoadAttachments.Point(UUID.randomUUID(),new V(x+40.5,212.25,z+.5),new V(x+40.5,210.25,z+.5),true);var oldB=new RoadAttachments.Point(UUID.randomUUID(),new V(x+80.5,210.25,z+.5),new V(x+80.5,210.25,z+.5),false);
   var legacy=AttachedPointCodec.write(new RoadAttachments.Data(List.of(oldA,oldB),List.of()));legacy.remove("ControlVersion");var migrated=AttachedPointCodec.read(legacy);var legacyMesh=r.withAttachments(migrated).mesh();
   h.assertTrue(legacyMesh.samples().stream().anyMatch(v->v.center().distance(oldB.position())<1e-6),"old implicit zero knot keeps its exact old shape after migration");
   h.assertTrue(!AttachedPointCodec.read(AttachedPointCodec.write(new RoadAttachments.Data(List.of(oldA,oldB),List.of()))).points().get(1).controlled(),"new metadata does not turn ordinary markers into deformation knots");
   var p=add(d,l,r,x+40.5,210,z+.5);r=d.index.roads.get(r.id()).record;var t=AttachedPointTool.payload(r,p);t.put("Offset",RoadRecord.writeNode(new Node(new V(0,2,mode==Mode.CURVE?2:0),0,0)));AttachedPointTool.edit(l,null,t);r=d.index.roads.get(r.id()).record;
   var edited=r.settings().options().attachments().points().get(0);h.assertTrue(edited.origin().equals(p.position())&&edited.position().distance(p.position().add(new V(0,2,mode==Mode.CURVE?2:0)))<1e-6,"relative offset is measured from immutable placement, not world zero");
   var beforeAdd=r.mesh().samples();var extra=add(d,l,r,x+100.5,210,z+.5);r=d.index.roads.get(r.id()).record;h.assertTrue(beforeAdd.equals(r.mesh().samples()),"adding a marker neither freezes controls nor changes existing deformation");
   h.assertTrue(r.settings().options().attachments().points().stream().filter(v->v.id().equals(p.id())).findFirst().orElseThrow().controlled(),"original control stays removable after another marker is added");
   t=AttachedPointTool.payload(r,edited);t.putBoolean("Delete",true);AttachedPointTool.edit(l,null,t);r=d.index.roads.get(r.id()).record;
   h.assertTrue(r.settings().options().attachments().points().stream().noneMatch(v->v.id().equals(p.id()))&&r.settings().options().attachments().points().stream().anyMatch(v->v.id().equals(extra.id())),"deleted control is not resurrected by ownership reconciliation");
   for(var sample:base.samples())h.assertTrue(RoadQueries.project(r.mesh(),sample.center()).sample().center().distance(sample.center())<1e-5,"deleting the only controller restores base alignment and removes its height/curve influence");
   var reloaded=RoadData.load(d.save(new CompoundTag())).index.roads.get(r.id()).record;h.assertTrue(reloaded.settings().options().attachments().equals(r.settings().options().attachments()),"restored geometry and origins survive reload");z+=300;
  }h.succeed();
 }

}
