package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.CorridorPlanner.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import com.sora.splineroads.SplineRoads;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_revision34") @PrefixGameTestTemplate(false)
public final class Revision34GameTests {
 static Settings road(Style s){return Revision32GameTests.road(s,Structure.GROUND);}
 static BlockPos marker(GameTestHelper h,int x,int y,int z,double yaw){return Revision32GameTests.marker(h,x,y,z,yaw);}
 @GameTest(template="empty",templateNamespace="splineroads_revision34",timeoutTicks=12000)
 public static void asphaltYCornerPersistsAndRebuilds(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);var a=marker(h,55000,2,55000,180);var b=marker(h,55028,2,54900,180);var c=marker(h,54972,2,54900,0);
  var aa=marker(h,55000,2,55040,180);var bb=marker(h,55028,2,54860,180);var cc=marker(h,54972,2,54860,0);
  var walk=new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,5,"minecraft:bricks");var main=road(Style.O4_YELLOW);main=main.options(main.options().sidewalk(walk).cycleFinish(RoadProfile.Options.CycleFinish.ASPHALT));var one=road(Style.O2_ONE);one=one.options(one.options().sidewalk(walk).cycleFinish(RoadProfile.Options.CycleFinish.ASPHALT));
  var ra=d.connect(l,null,aa,a,main,null);var rb=d.connect(l,null,b,bb,one,null);var rc=d.connect(l,null,cc,c,one,null);
  var section=RoadTransitions.Section.of(main);var round=RoadRecord.readSettings(RoadRecord.writeSettings(RoadTransitions.ends(main,section,section)));h.assertTrue(round.options().ends().start().cycleAsphalt(),"endpoint material NBT preserves asphalt");
  long[] pts={a.asLong(),b.asLong(),c.asLong()};var tool=new ItemStack(SplineRoads.Y_JUNCTION.get());tool.getOrCreateTag().putLongArray("Points",pts);tool.getOrCreateTag().putString("Dimension",l.dimension().location().toString());
  YJunctionTool.build(l,null,YJunctionTool.payload(l,null,pts,null),tool);UUID group=d.interchanges.entrySet().stream().filter(e->Arrays.equals(e.getValue().getLongArray("Points"),pts)).findFirst().orElseThrow().getKey();
  for(int pass=0;pass<2;pass++){
   var roads=d.index.roads.values().stream().filter(r->group.equals(r.record.assembly())).toList();
   for(var r:roads)for(Sample s:r.mesh.samples())h.assertTrue(!RoadTransitions.greenCycle(r.mesh,s),"Y never paints asphalt lanes green");
   var stem=roads.stream().filter(r->RoadProfile.catalog(r.mesh.settings().style()).twoWay()).findFirst().orElseThrow();var out=roads.stream().filter(r->r!=stem&&r.record.a().equals(stem.record.b())).findFirst().orElseThrow();var in=roads.stream().filter(r->r!=stem&&r.record.b().equals(stem.record.b())).findFirst().orElseThrow();var pocket=YJunctionWalks.plan(stem.mesh,out.mesh,in.mesh);h.assertTrue(pocket!=null,"island corner planned");
   var parts=roads.stream().flatMap(r->r.record.structures().stream()).toList();
   for(var p:pocket.tactile())if(p.width()>.1){V point=p.a().add(p.b()).mul(.5);h.assertTrue(parts.stream().filter(q->q.material()==RoadStructures.Material.TACTILE).anyMatch(q->JunctionPaint.inside(q.base(),point)),"actual built corner has continuous tactile");h.assertTrue(parts.stream().filter(q->q.material()==RoadStructures.Material.WALK_BRICKS).anyMatch(q->JunctionPaint.inside(q.base(),point)),"actual tactile lies on brick sidewalk");}
   var saved=RoadData.load(d.save(new CompoundTag()));h.assertTrue(saved.index.roads.get(out.record.id()).record.structures().equals(out.record.structures()),"corner structures persist exactly");
   var edit=YJunctionTool.payload(l,null,pts,group);edit.putDouble("Tension",.45);YJunctionTool.build(l,null,edit,tool);
  }
  Interchanges.remove(l,null,group);for(var r:List.of(ra,rb,rc))d.remove(l,null,r.id());h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_revision34",timeoutTicks=12000)
 public static void slopingCurvedFrontageKeepsMainAndIndependentRoad(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);var a=marker(h,56000,2,56000,-75);var b=marker(h,56160,8,56000,-105);
  var base=road(Style.O2_YELLOW);base=new Settings(Mode.CURVE,base.style(),base.width(),base.thickness(),.4,90).options(base.options()).structure(Structure.GROUND);
  var original=d.connect(l,null,a,b,base,null);Mesh authored=original.mesh();h.assertTrue(RoadAxis.of(authored).curved(),"fixture main has real curve");
  var u=marker(h,56000,2,56100,-90);var v=marker(h,56160,2,56100,-90);var unrelated=d.connect(l,null,u,v,road(Style.O2_YELLOW),null);var exact=d.index.roads.get(unrelated.id());var snapshot=exact.record.save();
  long[] pts={a.asLong(),b.asLong()};var inputs=Corridors.initialize(Interchanges.payload(l,null,pts,null),Kind.FRONTAGE);
  inputs.put("Corridor",Corridors.write(new Config(Kind.FRONTAGE,Sides.BOTH,Access.BOTH,12,90,Adjustment.AUTO)));
  var preview=Corridors.preview(l,null,inputs);h.assertTrue(Corridors.read(preview.getCompound("Corridor")).access()==Access.NONE,"server locks no ramps");h.assertTrue(Corridors.read(preview.getCompound("Corridor")).frontageY()==original.start().position().y(),"server forces A height");
  var plan=Interchanges.plan(preview);h.assertTrue(plan.movements()==0,"slope preview has no ramps");Interchanges.build(l,null,inputs);
  UUID group=d.interchanges.entrySet().stream().filter(e->Arrays.equals(e.getValue().getLongArray("Points"),pts)).findFirst().orElseThrow().getKey();
  for(int pass=0;pass<2;pass++){
   var reopen=Interchanges.payload(l,null,pts,group);var rebuilt=Interchanges.plan(reopen);h.assertTrue(rebuilt.movements()==0,"no ramps after reopening");
   var main=rebuilt.legs().get(0).mesh();for(Sample s:main.samples()){var q=RoadQueries.horizontal(authored,s.center());h.assertTrue(s.center().distance(q.sample().center())<.02,"main curve and elevation unchanged");}
   for(var leg:rebuilt.legs())if(leg.name().contains("辅路"))for(Sample s:leg.mesh().samples())h.assertTrue(Math.abs(s.center().y()-original.start().position().y())<1e-7,"all frontage at A height");
   h.assertTrue(exact==d.index.roads.get(unrelated.id())&&snapshot.equals(exact.record.save()),"unrelated road unchanged");
   Interchanges.build(l,null,reopen);
  }
  var reload=RoadData.load(d.save(new CompoundTag()));h.assertTrue(HostAxes.curved(reload.interchanges.get(group)),"saved curved host survives reload");
  Interchanges.remove(l,null,group);d.remove(l,null,unrelated.id());h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_revision34",timeoutTicks=12000)
 public static void curvedInterchangePayloadAndReopenKeepAxes(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);var a=marker(h,57600,20,58000,-88);var b=marker(h,58400,20,58000,-92);var c=marker(h,58000,48,57600,2);var e=marker(h,58000,48,58400,-2);
  var base=road(Style.O2_YELLOW);var s=new Settings(Mode.CURVE,base.style(),base.width(),base.thickness(),.4,90).options(base.options()).structure(Structure.GROUND);
  var ab=d.connect(l,null,a,b,s,null);var cd=d.connect(l,null,c,e,s,null);
  long[] pts={a.asLong(),b.asLong(),c.asLong(),e.asLong()};var inputs=Interchanges.payload(l,null,pts,null);h.assertTrue(HostAxes.curved(inputs),"curved hosts enter payload");h.assertTrue(HostAxes.presets(inputs).equals(List.of(Preset.CLOVERLEAF,Preset.HYBRID,Preset.STACK)),"server offers exactly three curved layouts");
  inputs.put("Options",Interchanges.write(new Options(Preset.HYBRID,false,1,96,16,5,1).adjust(true)));
  var plan=Interchanges.plan(inputs);h.assertTrue(plan.movements()==8,"curved four-way keeps all movements");
  // Persisted descriptor must retain both paths even when endpoint fitting extends them.
  HostAxes.fit(inputs,plan.anchors());ListTag n=new ListTag();plan.anchors().forEach(p->n.add(RoadRecord.writeNode(p)));inputs.put("Nodes",n);
  var reopened=Interchanges.plan(inputs);h.assertTrue(reopened.legs().get(0).mesh().first().center().distance(plan.legs().get(0).mesh().first().center())<.02,"fitted path reopens stably");
  for(int axis=0;axis<2;axis++)for(Sample sample:reopened.legs().get(axis).mesh().samples())h.assertTrue(sample.center().sub(HostAxes.read(inputs).get(axis).at(HostAxes.read(inputs).get(axis).project(sample.center()))).horizontalLength()<.03,"reopened main follows original curve");
  var groups=new HashSet<>(d.interchanges.keySet());Interchanges.build(l,null,inputs);
  UUID group=d.interchanges.keySet().stream().filter(id->!groups.contains(id)).findFirst().orElseThrow();var saved=d.interchanges.get(group);var points=saved.getLongArray("Points");
  var actual=d.index.roads.values().stream().filter(r->group.equals(r.record.assembly())).toList();h.assertTrue(actual.size()==reopened.legs().size(),"all curved interchange roads physically built");
  var reload=RoadData.load(d.save(new CompoundTag()));for(var r:actual)h.assertTrue(reload.index.roads.get(r.record.id()).mesh.samples().equals(r.mesh.samples()),"curved interchange geometry survives world save");
  var edit=Interchanges.payload(l,null,points,group);Interchanges.build(l,null,edit);h.assertTrue(d.interchanges.get(group).getList("Nodes",Tag.TAG_COMPOUND).equals(saved.getList("Nodes",Tag.TAG_COMPOUND)),"whole edit retains fitted anchors");
  Interchanges.remove(l,null,group);h.succeed();
 }
}
