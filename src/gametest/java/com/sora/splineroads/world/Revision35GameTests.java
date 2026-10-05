package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import com.sora.splineroads.SplineRoads;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_revision35") @PrefixGameTestTemplate(false)
public final class Revision35GameTests {
 @GameTest(template="empty",templateNamespace="splineroads_revision34",timeoutTicks=12000)
 public static void unequalYBuildReopenAndPersist(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);var a=Revision32GameTests.marker(h,62000,2,62000,180);var b=Revision32GameTests.marker(h,62060,2,61820,180);var c=Revision32GameTests.marker(h,61940,2,61820,0);
  var aa=Revision32GameTests.marker(h,62000,2,62040,180);var bb=Revision32GameTests.marker(h,62060,2,61780,180);var cc=Revision32GameTests.marker(h,61940,2,61780,0);
  var ra=d.connect(l,null,aa,a,Revision34GameTests.road(Style.O6_YELLOW),null);var rb=d.connect(l,null,b,bb,Revision34GameTests.road(Style.O1_ONE),null);var rc=d.connect(l,null,cc,c,Revision34GameTests.road(Style.O2_ONE),null);
  long[] points={a.asLong(),b.asLong(),c.asLong()};var tool=new ItemStack(SplineRoads.Y_JUNCTION.get());tool.getOrCreateTag().putLongArray("Points",points);tool.getOrCreateTag().putString("Dimension",l.dimension().location().toString());
  YJunctionTool.build(l,null,YJunctionTool.payload(l,null,points,null),tool);UUID group=d.interchanges.entrySet().stream().filter(e->Arrays.equals(e.getValue().getLongArray("Points"),points)).findFirst().orElseThrow().getKey();
  for(int i=0;i<2;i++){
   var roads=d.index.roads.values().stream().filter(r->group.equals(r.record.assembly())).toList();h.assertTrue(roads.size()==3,"all three Y legs built");
   var out=roads.stream().filter(r->r.record.b().equals(b)).findFirst().orElseThrow();var in=roads.stream().filter(r->r.record.a().equals(c)).findFirst().orElseThrow();
   h.assertTrue(RoadProfile.catalog(out.mesh.settings().style()).lanes()==1,"A to B uses one-lane target");h.assertTrue(RoadProfile.catalog(in.mesh.settings().style()).lanes()==3,"C to A uses three receiving lanes");
   var reload=RoadData.load(d.save(new CompoundTag()));h.assertTrue(reload.index.roads.get(in.record.id()).mesh.samples().equals(in.mesh.samples()),"unequal transition survives NBT");
   YJunctionTool.build(l,null,YJunctionTool.payload(l,null,points,group),tool);
  }
  Interchanges.remove(l,null,group);for(var r:List.of(ra,rb,rc))d.remove(l,null,r.id());h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_revision34",timeoutTicks=12000)
 public static void curvedStackAutomaticallyRaisesAndCommitsEndpoints(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);
  var a=Revision32GameTests.marker(h,63600,20,64000,-84);var b=Revision32GameTests.marker(h,64400,20,64000,-96);
  var c=Revision32GameTests.marker(h,64000,30,63600,6);var e=Revision32GameTests.marker(h,64000,30,64400,-6);
  var base=Revision34GameTests.road(Style.H4_RAIL);var settings=new Settings(Mode.CURVE,base.style(),base.width(),base.thickness(),.4,90).options(base.options()).structure(Structure.GROUND);
  d.connect(l,null,a,b,settings,null);d.connect(l,null,c,e,settings,null);
  long[] pts={a.asLong(),b.asLong(),c.asLong(),e.asLong()};var input=Interchanges.payload(l,null,pts,null);
  input.put("Options",Interchanges.write(new Options(Preset.STACK,false,1,96,20,5,1).adjust(true)));
  var plan=Interchanges.plan(input);h.assertTrue(plan.anchors().get(2).position().y()-plan.anchors().get(0).position().y()>=21,"low gap automatically raised");
  var groups=new HashSet<>(d.interchanges.keySet());Interchanges.build(l,null,input);
  UUID group=d.interchanges.keySet().stream().filter(id->!groups.contains(id)).findFirst().orElseThrow();var stored=d.interchanges.get(group);var savedNodes=stored.getList("Nodes",Tag.TAG_COMPOUND);
  h.assertTrue(RoadRecord.readNode(savedNodes.getCompound(2)).position().y()-RoadRecord.readNode(savedNodes.getCompound(0)).position().y()>=21,"committed endpoints have fitted height");
  var edit=Interchanges.payload(l,null,stored.getLongArray("Points"),group);var again=Interchanges.plan(edit);
  for(int i=0;i<4;i++)h.assertTrue(again.anchors().get(i).position().distance(plan.anchors().get(i).position())<.02,"reopening does not shift adjusted endpoints");
  Interchanges.remove(l,null,group);h.succeed();
 }

}
