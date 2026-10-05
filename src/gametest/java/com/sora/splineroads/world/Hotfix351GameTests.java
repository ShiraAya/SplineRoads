package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;
import java.util.*;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.net.RoadNetwork;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
@GameTestHolder("splineroads_hotfix351") @PrefixGameTestTemplate(false)
public final class Hotfix351GameTests {
 @GameTest(template="empty",templateNamespace="splineroads_hotfix351",timeoutTicks=12000)
 public static void elevatedRoadWithNoLowerRoadAndConnectedExtension(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);int x=76000,z=76000;
  var a=Revision32GameTests.marker(h,x,15,z,-75);var b=Revision32GameTests.marker(h,x+240,15,z,-105);var c=Revision32GameTests.marker(h,x+450,15,z-80,-100);
  var base=Revision32GameTests.road(Style.O6_YELLOW,Structure.AUTO);var settings=new Settings(Mode.CURVE,base.style(),base.width(),1,.4,90).options(base.options());
  var one=d.connect(l,null,a,b,settings,null);var two=d.connect(l,null,b,c,settings,null);
  for(int pass=0;pass<2;pass++){
   for(var id:List.of(one.id(),two.id())){var r=d.index.roads.get(id);long piers=r.record.structures().stream().filter(p->p.pier()&&p.material()==Material.CONCRETE&&p.height()>2).count();h.assertTrue(piers>=7,"long clear AUTO curve has piers: "+piers);}
   d.connect(l,null,b,c,settings,two.id());
  }
  d.remove(l,null,two.id());d.remove(l,null,one.id());h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_hotfix351",timeoutTicks=12000)
 public static void elevatedManualJunctionNeedsPiers(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);int x=78000,z=78000;
  var center=Revision32GameTests.marker(h,x,15,z,0);var a=Revision32GameTests.marker(h,x,15,z+150,180);var b=Revision32GameTests.marker(h,x+150,15,z,90);var c=Revision32GameTests.marker(h,x-150,15,z,-90);
  long[] pts={center.asLong(),a.asLong(),b.asLong(),c.asLong()};var input=Junctions.payload(l,null,pts,null);Junctions.build(l,null,input);
  UUID group=d.junctions.entrySet().stream().filter(e->Arrays.equals(pts,e.getValue().getLongArray("Points"))).findFirst().orElseThrow().getKey();
  for(var r:d.index.roads.values())if(group.equals(r.record.assembly())&&r.record.junction().get().arm()>=0)h.assertTrue(r.record.structures().stream().anyMatch(p->p.pier()&&p.material()==Material.CONCRETE&&p.height()>2),"elevated junction approach has piers");
  var parts=d.index.roads.values().stream().filter(r->group.equals(r.record.assembly())).flatMap(r->r.record.structures().stream()).toList();
  h.assertTrue(parts.stream().anyMatch(p->p.pier()&&p.material()==Material.CONCRETE&&p.height()>2&&p.a().sub(new V(x+.5,p.a().y(),z+.5)).horizontalLength()<2),"central pad also has a support");
  var west=Revision32GameTests.marker(h,x-200,2,z,-90);var east=Revision32GameTests.marker(h,x+200,2,z,-90);
  var lower=d.connect(l,null,west,east,Revision32GameTests.road(Style.O8_YELLOW,Structure.GROUND),null);
  for(var r:d.index.roads.values())if(group.equals(r.record.assembly()))for(var part:r.record.structures())if(part.material()==Material.CONCRETE)h.assertTrue(!RoadInteractions.invades(part,d.index.roads.get(lower.id()).mesh),"all elevated junction supports clear later lower road");
  var loaded=RoadData.load(d.save(new CompoundTag()));for(var r:d.index.roads.values())if(group.equals(r.record.assembly()))h.assertTrue(loaded.index.roads.get(r.record.id()).record.structures().equals(r.record.structures()),"manual junction supports survive reload");
  d.remove(l,null,lower.id());Junctions.build(l,null,Junctions.payload(l,null,pts,group));
  Junctions.remove(l,null,group);h.succeed();
 }

 @GameTest(template="empty",templateNamespace="splineroads_hotfix351",timeoutTicks=12000)
 public static void elevatedAutomaticJunctionAndReload(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);int x=80000,z=80000;
  var a=Revision32GameTests.marker(h,x,10,z,-90);var b=Revision32GameTests.marker(h,x+180,10,z,-90);var c=Revision32GameTests.marker(h,x+180,10,z+180,0);
  var settings=Revision32GameTests.road(Style.O6_YELLOW,Structure.AUTO);var one=d.connect(l,null,a,b,settings,null);var two=d.connect(l,null,b,c,settings,null,false,true,false);
  UUID group=d.junctions.entrySet().stream().filter(e->e.getValue().getLong("CenterPos")==b.asLong()).findFirst().orElseThrow().getKey();
  for(int pass=0;pass<2;pass++){
   var all=d.index.roads.values().stream().filter(r->group.equals(r.record.assembly())).toList();
   h.assertTrue(!all.isEmpty(),"actual auto junction generated");
   h.assertTrue(all.stream().flatMap(r->r.record.structures().stream()).anyMatch(p->p.pier()&&p.material()==Material.CONCRETE&&p.height()>2),"auto junction on clear land has supports");
   var loaded=RoadData.load(d.save(new CompoundTag()));
   for(var r:all)h.assertTrue(loaded.index.roads.get(r.record.id()).record.structures().equals(r.record.structures()),"auto support geometry survives reload");
   d.connect(l,null,a,b,d.streets.get(one.id()).settings(),one.id());
  }
  d.remove(l,null,two.id());d.remove(l,null,one.id());h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_hotfix351",timeoutTicks=12000)
 public static void registeredToolSelectBuildEditDelete(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);int x=82000,z=82000;
  var player=FakePlayerFactory.getMinecraft(l);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
  var item=SplineRoads.JUNCTION.get();h.assertTrue(item instanceof JunctionTool,"registry exposes actual intersection editor");
  var tab=SplineRoads.TAB.get();tab.buildContents(new CreativeModeTab.ItemDisplayParameters(l.enabledFeatures(),true,l.registryAccess()));
  h.assertTrue(tab.getDisplayItems().stream().anyMatch(v->v.is(item))&&tab.getSearchTabDisplayItems().stream().anyMatch(v->v.is(item)),"intersection editor is in creative and search tabs");
  var center=Revision32GameTests.marker(h,x,2,z,0);var a=Revision32GameTests.marker(h,x,2,z+100,180);var b=Revision32GameTests.marker(h,x+22,2,z-100,180);var c=Revision32GameTests.marker(h,x-22,2,z-100,0);
  var aa=Revision32GameTests.marker(h,x,2,z+140,180);var bb=Revision32GameTests.marker(h,x+22,2,z-140,180);var cc=Revision32GameTests.marker(h,x-22,2,z-140,0);
  for(var p:List.of(center,a,b,c,aa,bb,cc))((NodeEntity)l.getBlockEntity(p)).owner=player.getUUID();
  var external=List.of(d.connect(l,player,aa,a,Revision34GameTests.road(Style.O6_YELLOW),null),d.connect(l,player,b,bb,Revision34GameTests.road(Style.O1_ONE),null),d.connect(l,player,cc,c,Revision34GameTests.road(Style.O2_ONE),null));
  var stack=new ItemStack(item);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
  for(var pos:List.of(center,a,b,c)){
   player.moveTo(pos.getX()+.5,pos.getY()+2,pos.getZ()+.5,0,0);
   item.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)));
  }
  long[] points={center.asLong(),a.asLong(),b.asLong(),c.asLong()};h.assertTrue(Arrays.equals(points,stack.getTag().getLongArray("Points")),"real item clicks retain center and three mouths");
  var input=Junctions.payload(l,player,points,null);h.assertTrue(JunctionPlanner.separatedEntrances(JunctionCodec.read(input.getCompound("Spec"))),"selected one-way pair recognized");input.putString("Action","junction");RoadNetwork.forget(player.getUUID());
  RoadNetwork.perform(player,input);UUID group=d.junctions.entrySet().stream().filter(e->Arrays.equals(points,e.getValue().getLongArray("Points"))).findFirst().orElseThrow().getKey();
  h.assertTrue(!stack.getTag().contains("Points"),"successful real packet consumes selection");
  var edit=Junctions.payload(l,player,points,group);edit.putString("Action","junction");RoadNetwork.forget(player.getUUID());RoadNetwork.perform(player,edit);
  var remove=new CompoundTag();remove.putString("Action","junctionDelete");remove.putUUID("Id",group);RoadNetwork.forget(player.getUUID());RoadNetwork.perform(player,remove);
  h.assertTrue(!d.junctions.containsKey(group),"same registered tool edits and deletes");for(var r:external){h.assertTrue(d.index.roads.containsKey(r.id()),"external roads retained");d.remove(l,player,r.id());}
  player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);RoadNetwork.forget(player.getUUID());h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_hotfix351",timeoutTicks=12000)
 public static void yThroatWorldCoordinatesSurviveBuildAndReload(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);int x=84000,z=84000;
  var a=Revision32GameTests.marker(h,x,2,z,180);var b=Revision32GameTests.marker(h,x+60,2,z-180,180);var c=Revision32GameTests.marker(h,x-60,2,z-180,0);
  var aa=Revision32GameTests.marker(h,x,2,z+40,180);var bb=Revision32GameTests.marker(h,x+60,2,z-220,180);var cc=Revision32GameTests.marker(h,x-60,2,z-220,0);
  var external=List.of(d.connect(l,null,aa,a,Revision34GameTests.road(Style.O6_YELLOW),null),d.connect(l,null,b,bb,Revision34GameTests.road(Style.O3_ONE),null),d.connect(l,null,cc,c,Revision34GameTests.road(Style.O3_ONE),null));
  long[] points={a.asLong(),b.asLong(),c.asLong()};var tool=new ItemStack(SplineRoads.Y_JUNCTION.get());tool.getOrCreateTag().putLongArray("Points",points);tool.getTag().putString("Dimension",l.dimension().location().toString());
  YJunctionTool.build(l,null,YJunctionTool.payload(l,null,points,null),tool);UUID group=d.interchanges.entrySet().stream().filter(e->Arrays.equals(points,e.getValue().getLongArray("Points"))).findFirst().orElseThrow().getKey();
  for(int pass=0;pass<2;pass++){
   var all=d.index.roads.values().stream().filter(r->group.equals(r.record.assembly())).toList();var stem=all.stream().filter(r->r.record.a().equals(a)).findFirst().orElseThrow();
   for(var branch:all)if(branch!=stem){boolean out=branch.record.b().equals(b);var end=out?branch.mesh.first():branch.mesh.last();var layout=RoadProfile.layout(branch.mesh,end);var target=layout.dividers().stream().map(v->end.at(v,0)).toList();var expected=RoadProfile.layout(stem.mesh,stem.mesh.last()).dividers().stream().map(v->stem.mesh.last().at(v,0)).filter(v->v.sub(stem.mesh.last().center()).dot(stem.mesh.last().left())*(out?1:-1)>0).toList();
    for(var point:expected)h.assertTrue(target.stream().anyMatch(v->v.distance(point)<1e-6),"built Y white lines meet stem exactly");
   }
   var reload=RoadData.load(d.save(new CompoundTag()));for(var road:all)h.assertTrue(reload.index.roads.get(road.record.id()).record.settings().equals(road.record.settings()),"exact throat profile and paint phase persisted");
   YJunctionTool.build(l,null,YJunctionTool.payload(l,null,points,group),new ItemStack(SplineRoads.JUNCTION.get()));
  }
  Interchanges.remove(l,null,group);for(var r:external)d.remove(l,null,r.id());h.succeed();
 }
}
