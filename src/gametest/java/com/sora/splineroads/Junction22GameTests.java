package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("splineroads_junction22") @PrefixGameTestTemplate(false)
public final class Junction22GameTests {
  static BlockPos node(GameTestHelper h,int x,int z){BlockPos p=new BlockPos(x,90,z);h.getLevel().setBlock(p,SplineRoads.NODE.get().defaultBlockState(),3);((NodeEntity)h.getLevel().getBlockEntity(p)).owner=new UUID(0,0);return p;}
  // Far-away GameTest coordinates use seed-dependent terrain. Manual infill tests
  // require an empty deck slice, otherwise preserved stone already fills every cell.
  static void clearInfillDeck(GameTestHelper h,int ax,int az,int bx,int bz){
    for(int x=Math.min(ax,bx)-6;x<=Math.max(ax,bx)+6;x++)
      for(int z=Math.min(az,bz)-6;z<=Math.max(az,bz)+6;z++)
        h.getLevel().setBlock(new BlockPos(x,89,z),Blocks.AIR.defaultBlockState(),2);
  }
  static Settings settings(){return new Settings(Mode.STRAIGHT,Style.O2_YELLOW,9,1,.35,90).structure(Structure.GROUND);}
  @GameTest(template="empty",templateNamespace="splineroads_junction22",timeoutTicks=600)
  public static void rightClickInfillHonorsCancelAndConsumesOneBlock(GameTestHelper h){
    clearInfillDeck(h,-54500,-54500,-54460,-54430);
    var l=h.getLevel();var d=RoadData.get(l);BlockPos a=node(h,-54500,-54500),b=node(h,-54460,-54430);
    var aa=(NodeEntity)l.getBlockEntity(a);var bb=(NodeEntity)l.getBlockEntity(b);aa.yaw=-90;bb.yaw=0;aa.headingLocked=bb.headingLocked=true;
    var road=d.connect(l,null,a,b,new Settings(Mode.AUTO,Style.O2_YELLOW,9,1,.35,90).structure(Structure.GROUND),null);
    var fill=Blocks.GRAY_CONCRETE.defaultBlockState();
    var p=d.index.roads.get(road.id()).cells.keySet().stream().map(BlockPos::of).filter(pos->RoadBlocks.canInfill(l,pos,fill)).findFirst().orElseThrow();
    var player=net.minecraftforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(new UUID(0,0),"SR22Tester"));
    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(Blocks.GRAY_CONCRETE,3));
    java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent> deny=event->{if(event.getEntity()==player){h.assertTrue(event.getPlacedBlock().equals(fill),"protection event exposes the requested concrete");event.setCanceled(true);}};
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent.class,deny);
    var hit=new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(p.getX(),p.getY()+.5,p.getZ()+.5),net.minecraft.core.Direction.EAST,p.west(),false);
    try{RoadEvents.infill(new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,net.minecraft.world.InteractionHand.MAIN_HAND,p.west(),hit));}
    finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny);}
    h.assertTrue(l.getBlockState(p).is(SplineRoads.COLLIDER.get())&&player.getMainHandItem().getCount()==3,"cancelled placement leaves road and inventory unchanged");
    var event=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,net.minecraft.world.InteractionHand.MAIN_HAND,p.west(),hit);RoadEvents.infill(event);
    h.assertTrue(event.isCanceled()&&l.getBlockEntity(p) instanceof RoadFillEntity f&&f.fill().equals(fill),"right click fills the partial curve cell");
    h.assertTrue(player.getMainHandItem().getCount()==2,"survival placement consumes exactly one block");
    d.remove(l,null,road.id());h.assertTrue(l.getBlockState(p).equals(fill),"event-placed fill survives road removal");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_junction22",timeoutTicks=600)
  public static void partialSideAndEndInfillSurviveEditReloadAndRemoval(GameTestHelper h){
    var l=h.getLevel();var d=RoadData.get(l);var fill=Blocks.GRAY_CONCRETE.defaultBlockState();
    clearInfillDeck(h,-53000,-53000,-52940,-52960);
    BlockPos a=node(h,-53000,-53000),b=node(h,-52940,-52960);
    var road=d.connect(l,null,a,b,settings(),null);var built=d.index.roads.get(road.id());
    var candidates=built.cells.keySet().stream().map(BlockPos::of).filter(p->RoadBlocks.canInfill(l,p,fill)).sorted(Comparator.comparingDouble(p->p.distSqr(a))).toList();
    h.assertTrue(candidates.size()>4,"diagonal road has fillable partial side/end cells at negative coordinates");
    BlockPos near=candidates.get(0),side=candidates.get(candidates.size()/2),far=candidates.get(candidates.size()-1);
    for(BlockPos p:List.of(near,side,far)){
      h.assertTrue(d.infill(l,p,fill,null),"can fill partial deck cell "+p);
      h.assertTrue(l.getBlockEntity(p) instanceof RoadFillEntity,"fill entity exists only at filled cell");
      var entity=(RoadFillEntity)l.getBlockEntity(p);var snapshot=entity.saveWithFullMetadata();
      entity.fill(Blocks.AIR.defaultBlockState());entity.load(snapshot);
      h.assertTrue(entity.fill().equals(fill),"fill survives block entity serialization");
      h.assertTrue(!Shapes.joinIsNotEmpty(Shapes.block(),l.getBlockState(p).getCollisionShape(l,p),BooleanOp.ONLY_FIRST),"filled partial cell has no collision hole");
      h.assertTrue(!d.infill(l,p,fill,null),"cannot consume a second block on an already filled cell");
    }
    h.assertTrue(!d.infill(l,side,Blocks.CHEST.defaultBlockState(),null),"block entities are not disguised as infill");
    var reloaded=RoadData.load(d.save(new CompoundTag()));
    h.assertTrue(reloaded.index.roads.containsKey(road.id()),"road data survives reload");
    d.connect(l,null,a,b,settings(),road.id());
    for(BlockPos p:List.of(near,side,far))h.assertTrue(l.getBlockEntity(p) instanceof RoadFillEntity f&&f.fill().equals(fill),"road rebuild preserves manual infill");
    h.assertTrue(d.excavateSupport(l,side,null),"infill can be dug out independently");
    h.assertTrue(l.getBlockState(side).is(SplineRoads.COLLIDER.get())&&!d.index.shape(side).isEmpty(),"digging fill retains road collision");
    h.assertTrue(d.infill(l,side,fill,null),"excavated gap can be filled again");
    d.remove(l,null,road.id());
    for(BlockPos p:List.of(near,side,far))h.assertTrue(l.getBlockState(p).equals(fill),"deleting road retains manually placed concrete");
    h.assertTrue(RoadWorkChunks.heldCount(l)==0,"infill lifecycle releases work chunks");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_junction22",timeoutTicks=1200)
  public static void twoArmCornerSignalsLegacyReferencesAndRingOption(GameTestHelper h){
    var l=h.getLevel();var d=RoadData.get(l);BlockPos c=node(h,53500,53500),a=node(h,53630,53500),b=node(h,53500,53630);
    var first=d.connect(l,null,c,a,settings(),null);var second=d.connect(l,null,c,b,settings(),null,false,true);
    var piece=d.index.roads.values().stream().filter(r->r.record.junction()!=null&&r.record.b().equals(c)).findFirst().orElseThrow();
    var edit=Junctions.payload(l,null,new long[0],piece.record.assembly());edit.getCompound("Spec").putString("Control","SIGNALS");Junctions.build(l,null,edit);
    var pieces=d.index.roads.values().stream().filter(r->r.record.junction()!=null&&r.record.b().equals(c)).toList();
    var spec=pieces.get(0).record.junction().spec();
    for(var move:JunctionPlanner.plan(spec).movements())for(V p:move.path())h.assertTrue(pieces.stream().anyMatch(r->RoadQueries.contains(r.mesh,p,.02,.1)),"two-arm actual built pavement covers every movement");
    h.assertTrue(pieces.stream().flatMap(r->r.signalHeads.stream()).anyMatch(p->p.material()==RoadStructures.Material.SIGNAL_PEDESTRIAN),"pedestrian lights created by world builder");
    var saved=RoadData.load(d.save(new CompoundTag()));
    for(var r:pieces){var copy=saved.index.roads.get(r.record.id());
      h.assertTrue(copy.record.structures().equals(r.record.structures()),"new signals persist in piece "+r.record.junction().piece());
      h.assertTrue(copy.mesh.equals(r.mesh),"new geometry persists in piece "+r.record.junction().piece());}
    for(int i=0;i<10;i++){
      var ref=new JunctionPlanner.Ref(spec,i,21);var tag=JunctionCodec.writeRef(ref);tag.remove("GeometryVersion");
      var read=JunctionCodec.readRef(tag);h.assertTrue(read.geometryVersion()==21&&read.get().mesh().equals(ref.get().mesh()),"0.21 unversioned piece index retains legacy geometry");
      h.assertTrue(JunctionCodec.readRef(JunctionCodec.writeRef(read)).equals(read),"resaving legacy ref never upgrades its index silently");
    }
    d.remove(l,null,second.id());d.remove(l,null,first.id());
    BlockPos center=node(h,54000,54000);var request=new CompoundTag();request.put("Node",RoadRecord.writeNode(((NodeEntity)l.getBlockEntity(center)).constructionNode()));request.putDouble("Radius",20);request.putInt("Lanes",2);request.putBoolean("OuterRail",true);
    var ring=RoundaboutTool.specification(request);h.assertTrue(ring.outerRail(),"roundabout placement reads outer rail option");
    for(var port:RoundaboutTool.ports(ring))l.setBlock(BlockPos.containing(port.position().x(),90,port.position().z()),Blocks.AIR.defaultBlockState(),3);AutoJunctions.createRing(l,null,center,ring);var group=AutoJunctions.center(d,center).getUUID("Id");
    var payload=Junctions.payload(l,null,new long[0],group);h.assertTrue(payload.getCompound("Spec").getBoolean("OuterRail"),"outer rail persists to edit screen");
    payload.getCompound("Spec").putBoolean("OuterRail",false);Junctions.build(l,null,payload);
    h.assertTrue(!Junctions.payload(l,null,new long[0],group).getCompound("Spec").getBoolean("OuterRail"),"outer rail can be removed by edit");
    AutoJunctions.removeCenter(l,null,group);h.succeed();
  }
}
