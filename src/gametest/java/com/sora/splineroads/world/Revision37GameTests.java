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
@GameTestHolder("splineroads_revision37") @PrefixGameTestTemplate(false)
public final class Revision37GameTests {
 @GameTest(template="empty",templateNamespace="splineroads_revision37",timeoutTicks=12000)
 public static void streetscapeWorldSaveRebuildAndRemoval(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);int x=120000,z=120000;
  for(int xx=x-8;xx<x+145;xx++)for(int zz=z-25;zz<z+26;zz++){var p=new BlockPos(xx,0,zz);level.getChunkAt(p);level.setBlock(p,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);}
  var a=Revision32GameTests.marker(h,x,1,z,-90);var b=Revision32GameTests.marker(h,x+128,1,z,-90);
  var o=RoadProfile.Options.DEFAULT.cycleFinish(RoadProfile.Options.CycleFinish.GREEN).sidewalk(RoadSidewalks.Config.DEFAULT.enabled(true).width(2)).streetscape(new RoadStreetscape.Config(RoadStreetscape.Separator.GREEN,false,32,true,40,RoadStreetscape.Planting.CHERRY,16)).cycleFinish(RoadProfile.Options.CycleFinish.GREEN);
  var s=new Settings(Mode.STRAIGHT,Style.O4_GREEN,RoadProfile.width(Style.O4_GREEN,o,4),1,.4,90).structure(Structure.GROUND).options(o);
  var r=data.connect(level,null,a,b,s,null);var built=data.index.roads.get(r.id());
  var trunks=built.record.structures().stream().filter(p->p.material()==Material.CHERRY_LOG&&p.pier()).toList();h.assertTrue(!trunks.isEmpty(),"trees built in actual world");
  for(var trunk:trunks){var p=BlockPos.containing(trunk.a().x(),trunk.a().y()+1,trunk.a().z());h.assertTrue(RoadBlocks.isCollider(level.getBlockState(p)),"tree uses an SR collision block, no vanilla growing/decaying tree");}
  h.assertTrue(built.record.settings().options().sidewalk().width()>=7,"world applies automatic sidewalk widening");
  var saved=data.save(new CompoundTag());var restored=RoadData.load(saved).index.roads.get(r.id()).record;
  h.assertTrue(restored.settings().options().streetscape().equals(o.streetscape()),"all amenities survive world reload");h.assertTrue(restored.structures().equals(built.record.structures()),"persisted tree and lamp structures survive reload");
  var parking=s.options(o.cycleFinish(RoadProfile.Options.CycleFinish.PARKING));parking=new Settings(parking.mode(),parking.style(),RoadProfile.width(parking.style(),parking.options(),4),parking.thickness(),parking.tension(),parking.arcDegrees()).structure(Structure.GROUND).options(parking.options());
  data.connect(level,null,a,b,parking,r.id());var updated=data.index.roads.get(r.id()).record;
  h.assertTrue(updated.settings().options().cycleFinish()==RoadProfile.Options.CycleFinish.PARKING&&!updated.settings().options().cycleRail(),"parking state applied in world");
  data.remove(level,null,r.id());for(var trunk:trunks){var p=BlockPos.containing(trunk.a().x(),trunk.a().y()+1,trunk.a().z());h.assertTrue(!RoadBlocks.isCollider(level.getBlockState(p)),"removal cleans up SR tree blocks");}h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_revision37",timeoutTicks=12000)
 public static void unifiedApproachEditorChangesOnlySelectedArm(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);var player=FakePlayerFactory.getMinecraft(l);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);int x=122000,z=122000;
  var center=Revision32GameTests.marker(h,x,2,z,0);var a=Revision32GameTests.marker(h,x+100,2,z,90);var b=Revision32GameTests.marker(h,x-80,2,z+75,0);var c=Revision32GameTests.marker(h,x-50,2,z-90,0);
  for(var p:List.of(center,a,b,c))((NodeEntity)l.getBlockEntity(p)).owner=player.getUUID();
  long[] points={center.asLong(),a.asLong(),b.asLong(),c.asLong()};Junctions.build(l,player,Junctions.payload(l,player,points,null));
  UUID group=d.junctions.entrySet().stream().filter(e->Arrays.equals(points,e.getValue().getLongArray("Points"))).findFirst().orElseThrow().getKey();
  var road=d.index.roads.values().stream().filter(v->group.equals(v.record.assembly())&&v.record.junction().get().arm()==1).findFirst().orElseThrow().record;
  var payload=JunctionRoads.payload(l,player,road);h.assertTrue(payload.contains("Settings"),"manual approach provides standard road settings");
  var before=JunctionCodec.read(payload.getCompound("Spec"));var o=RoadProfile.Options.DEFAULT.sidewalk(RoadSidewalks.Config.DEFAULT.enabled(true)).streetscape(RoadStreetscape.Config.DEFAULT.lampSpacing(36).planting(RoadStreetscape.Planting.BIRCH));
  var s=new Settings(Mode.STRAIGHT,Style.O6_YELLOW,RoadProfile.width(Style.O6_YELLOW,o,4),1,.4,90).structure(Structure.GROUND).options(o);
  var edit=new CompoundTag();edit.putString("Action","junctionRoad");edit.putUUID("Id",group);edit.putInt("Arm",1);edit.putInt("Signature",payload.getInt("Signature"));edit.put("Settings",RoadRecord.writeSettings(s));
  player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SplineRoads.CONNECTOR.get()));RoadNetwork.forget(player.getUUID());RoadNetwork.perform(player,edit);
  var after=JunctionCodec.read(Junctions.payload(l,player,points,group).getCompound("Spec"));
  h.assertTrue(after.arms().get(1).width()==s.width()&&after.arms().get(1).incoming()==3,"six-lane selection also expands total width");
  h.assertTrue(after.arms().get(1).external().options().streetscape().equals(o.streetscape()),"new approach options retained on reopen");
  for(int i:new int[]{0,2})h.assertTrue(after.arms().get(i).equals(before.arms().get(i)),"other directions stay unchanged");
  h.assertTrue(after.greenSeconds()==before.greenSeconds()&&after.control()==before.control(),"road settings preserve junction controls");
  h.assertTrue(RoadData.load(d.save(new CompoundTag())).junctions.get(group).getCompound("Spec").equals(d.junctions.get(group).getCompound("Spec")),"updated junction survives reload");
  Junctions.remove(l,player,group);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);RoadNetwork.forget(player.getUUID());h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_revision37",timeoutTicks=12000)
 public static void automaticPreviewAndEditAreScopedToOneStreet(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);int x=124000,z=124000;
  var center=Revision32GameTests.marker(h,x,2,z,-90);var a=Revision32GameTests.marker(h,x-100,2,z,-90);var b=Revision32GameTests.marker(h,x,2,z+100,0);var c=Revision32GameTests.marker(h,x+100,2,z,-90);
  var s=Revision32GameTests.road(Style.O4_YELLOW,Structure.GROUND);
  var one=d.connect(l,null,a,center,s,null);var two=d.connect(l,null,center,b,s,null,false,true,false);var three=d.connect(l,null,center,c,s,null);
  var otherSettings=new HashMap<UUID,Settings>();for(var r:d.streets.values())if(!r.id().equals(one.id()))otherSettings.put(r.id(),r.settings());
  var command=new CompoundTag();command.putUUID("Id",one.id());command.putLong("A",a.asLong());command.putLong("B",center.asLong());command.put("Settings",RoadRecord.writeSettings(s));command.put("StartNode",RoadRecord.writeNode(one.start()));command.put("EndNode",RoadRecord.writeNode(one.end()));AutoJunctions.enrich(command,d,a,center,one.id());
  var draft=AutoJunctions.preview(command);var selected=AutoJunctions.selected(draft,one.id());
  h.assertTrue(!selected.isEmpty()&&selected.size()<draft.roads().size(),"only selected street is highlighted");h.assertTrue(selected.stream().noneMatch(r->r.junction()!=null&&r.junction().get().arm()<0),"center triangles do not turn entire junction blue");
  var next=Revision32GameTests.road(Style.O6_YELLOW,Structure.GROUND);d.connect(l,null,a,center,next,one.id());
  for(var entry:otherSettings.entrySet()){var actual=d.streets.get(entry.getKey()).settings();h.assertTrue(actual.style()==entry.getValue().style()&&actual.width()==entry.getValue().width()&&actual.options().streetscape().equals(entry.getValue().options().streetscape()),"editing one street preserves every other authored road profile");}
  d.remove(l,null,three.id());d.remove(l,null,two.id());d.remove(l,null,one.id());h.succeed();
 }
}
