package com.sora.splineroads.world;
import com.sora.splineroads.*;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.net.RoadNetwork;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_hotfix353") @PrefixGameTestTemplate(false)
public final class Hotfix353GameTests {
 @GameTest(template="empty",templateNamespace="splineroads_hotfix353",timeoutTicks=12000)
 public static void arrowSwitchPacketSaveAndRebuild(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);var player=FakePlayerFactory.getMinecraft(l);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);int x=88000,z=88000;
  var a=Revision32GameTests.marker(h,x,12,z,-90);var b=Revision32GameTests.marker(h,x+100,12,z,-90);for(var p:List.of(a,b))((NodeEntity)l.getBlockEntity(p)).owner=player.getUUID();
  var r=d.connect(l,player,a,b,Revision32GameTests.road(Style.O6_RAIL,Structure.AUTO),null);var original=d.index.roads.get(r.id());var blocks=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
  for(long column:original.columns.keySet()){var c=BlockPos.of(column);var p=new BlockPos(c.getX(),11,c.getZ());blocks.put(p,l.getBlockState(p));}
  player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SplineRoads.LANE_LINES.get()));
  for(boolean hide:new boolean[]{true,false,true}){
   var before=d.index.roads.get(r.id()).record;var t=new CompoundTag();t.putString("Action","laneLines");t.putUUID("Id",r.id());t.putInt("Signature",GantryTool.signature(before));t.putBoolean("HideArrows",hide);RoadNetwork.forget(player.getUUID());RoadNetwork.perform(player,t);
   var next=d.index.roads.get(r.id()).record;h.assertTrue(next.settings().options().hideArrows()==hide,"packet toggles arrows");h.assertTrue(next.structures().equals(before.structures()),"paint edit preserves piers/facilities");h.assertTrue(next.mesh().samples().equals(before.mesh().samples()),"paint edit preserves alignment");
   blocks.forEach((p,state)->h.assertTrue(l.getBlockState(p).equals(state),"paint edit never changes blocks"));
   h.assertTrue(RoadData.load(d.save(new CompoundTag())).index.roads.get(r.id()).record.settings().options().hideArrows()==hide,"switch survives save/reload");
  }
  var next=d.index.roads.get(r.id()).record;d.connect(l,player,a,b,next.settings(),r.id());h.assertTrue(d.index.roads.get(r.id()).record.settings().options().hideArrows(),"rebuild retains switch");
  d.remove(l,player,r.id());RoadNetwork.forget(player.getUUID());player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_hotfix353",timeoutTicks=12000)
 public static void roadToolEditsManualArmWithoutOpeningJunctionControls(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);var player=FakePlayerFactory.getMinecraft(l);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);int x=90000,z=90000;
  var center=Revision32GameTests.marker(h,x,2,z,0);var a=Revision32GameTests.marker(h,x+100,2,z,90);var b=Revision32GameTests.marker(h,x-80,2,z+75,0);var c=Revision32GameTests.marker(h,x-50,2,z-90,0);
  for(var p:List.of(center,a,b,c))((NodeEntity)l.getBlockEntity(p)).owner=player.getUUID();
  long[] points={center.asLong(),a.asLong(),b.asLong(),c.asLong()};var t=Junctions.payload(l,player,points,null);Junctions.build(l,player,t);UUID group=d.junctions.entrySet().stream().filter(e->Arrays.equals(points,e.getValue().getLongArray("Points"))).findFirst().orElseThrow().getKey();
  var road=d.index.roads.values().stream().filter(v->group.equals(v.record.assembly())&&v.record.junction().get().arm()==1).findFirst().orElseThrow().record;
  var payload=JunctionRoads.payload(l,player,road);h.assertTrue(payload.getString("Kind").equals("armRoad")&&payload.getInt("Arm")==1,"manual road opens road-only settings");
  var before=JunctionCodec.read(payload.getCompound("Spec"));var fields=JunctionRoads.fields(before.arms().get(1));fields.putDouble("Width",13);fields.putBoolean("Walk",true);fields.putInt("WalkWidth",4);
  var edit=new CompoundTag();edit.putString("Action","junctionRoad");edit.putUUID("Id",group);edit.putInt("Arm",1);edit.putInt("Signature",payload.getInt("Signature"));edit.put("Fields",fields);
  player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SplineRoads.CONNECTOR.get()));RoadNetwork.forget(player.getUUID());RoadNetwork.perform(player,edit);
  var fresh=Junctions.payload(l,player,points,group);var after=JunctionCodec.read(fresh.getCompound("Spec"));h.assertTrue(after.arms().get(1).width()==13&&after.arms().get(1).external().options().sidewalk().width()==4&&after.arms().get(1).external().options().sidewalk().enabled(),"road changes persist on reopen");
  for(int i:new int[]{0,2})h.assertTrue(before.arms().get(i).equals(after.arms().get(i)),"other arms preserved");h.assertTrue(before.control()==after.control()&&before.greenSeconds()==after.greenSeconds(),"junction control preserved");
  var loaded=RoadData.load(d.save(new CompoundTag()));h.assertTrue(loaded.junctions.get(group).getCompound("Spec").equals(d.junctions.get(group).getCompound("Spec")),"junction road save/reload");
  fresh.putString("Action","junction");boolean rejected=false;try{RoadNetwork.forget(player.getUUID());RoadNetwork.perform(player,fresh);}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected,"road connector cannot execute junction-wide editing");
  player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SplineRoads.JUNCTION.get()));RoadNetwork.forget(player.getUUID());RoadNetwork.perform(player,fresh);
  Junctions.remove(l,player,group);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);RoadNetwork.forget(player.getUUID());h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_hotfix353",timeoutTicks=12000)
 public static void automaticApproachResolvesOriginalRoad(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);int x=92000,z=92000;var a=Revision32GameTests.marker(h,x-100,2,z,-90);var b=Revision32GameTests.marker(h,x,2,z,-90);var c=Revision32GameTests.marker(h,x,2,z+100,0);
  var one=d.connect(l,null,a,b,Revision32GameTests.road(Style.O4_YELLOW,Structure.GROUND),null);var two=d.connect(l,null,b,c,Revision32GameTests.road(Style.O4_YELLOW,Structure.GROUND),null,false,true,false);
  UUID group=d.junctions.entrySet().stream().filter(e->e.getValue().getLong("CenterPos")==b.asLong()).findFirst().orElseThrow().getKey();int arms=0;
  for(var r:d.index.roads.values())if(group.equals(r.record.assembly())&&r.record.junction().get().arm()>=0){arms++;var logical=JunctionRoads.logical(d,r.record);h.assertTrue(logical!=null&&d.streets.containsKey(logical.id()),"automatic road arm resolves to editable logical street");h.assertTrue(logical.id().equals(one.id())||logical.id().equals(two.id()),"resolved original target");}
  h.assertTrue(arms==2,"both arms covered");d.remove(l,null,two.id());d.remove(l,null,one.id());h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_hotfix353",timeoutTicks=12000)
 public static void freeCenterPierBesideLowerRoadKeepsSingleShaft(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);int x=94000,z=94000;
  for(int xx=x-10;xx<x+115;xx++)for(int zz=z-18;zz<z+25;zz++){var p=new BlockPos(xx,0,zz);l.getChunkAt(p);l.setBlock(p,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);}
  var a=Revision32GameTests.marker(h,x,12,z,-90);var b=Revision32GameTests.marker(h,x+100,12,z,-90);
  var c=Revision32GameTests.marker(h,x-5,2,z+6,-90);var e=Revision32GameTests.marker(h,x+105,2,z+6,-90);
  var upper=d.connect(l,null,a,b,Revision32GameTests.road(Style.O4_YELLOW,Structure.AUTO),null);
  var lower=d.connect(l,null,c,e,Revision32GameTests.road(Style.O2_YELLOW,Structure.GROUND),null);var built=d.index.roads.get(upper.id());
  var shafts=built.record.structures().stream().filter(p->p.pier()&&p.material()==Material.CONCRETE&&p.height()>2).toList();h.assertTrue(shafts.size()>=4,"nearby lower-edge voxel must not erase unobstructed center shafts: "+shafts.size());
  for(var p:shafts){h.assertTrue(Math.abs(p.a().z()-(z+.5))<1e-6,"shaft stays at center instead of becoming sideways portal");h.assertTrue(!RoadInteractions.invades(p,d.index.roads.get(lower.id()).mesh),"center shaft has actual clearance");}
  d.remove(l,null,lower.id());d.remove(l,null,upper.id());h.succeed();
 }

}
