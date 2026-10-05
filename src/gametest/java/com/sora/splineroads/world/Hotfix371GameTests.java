package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_hotfix371") @PrefixGameTestTemplate(false)
public final class Hotfix371GameTests {
 static boolean base(Part p){return p.material()==Material.CONCRETE&&p.width()==.38&&p.height()==.16;}
 @GameTest(template="empty",templateNamespace="splineroads_hotfix371",timeoutTicks=12000)
 public static void automaticBridgeClassificationAndParkingSurviveReload(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);int x=128000,z=128000;
  for(int xx=x-8;xx<x+50;xx++)for(int zz=z-24;zz<z+25;zz++){var p=new BlockPos(xx,0,zz);level.getChunkAt(p);level.setBlock(p,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);}
  var a=Revision32GameTests.marker(h,x,1,z,-90);var b=Revision32GameTests.marker(h,x+96,1,z,-90);
  var o=RoadProfile.Options.DEFAULT.cycleFinish(RoadProfile.Options.CycleFinish.PARKING).sidewalk(RoadSidewalks.Config.DEFAULT.enabled(true)).streetscape(RoadStreetscape.Config.DEFAULT.parking(true).planting(RoadStreetscape.Planting.OAK));
  var s=new Settings(Mode.STRAIGHT,Style.O4_YELLOW,RoadProfile.width(Style.O4_YELLOW,o,4),1,.4,90).structure(Structure.AUTO).options(o);
  var road=data.connect(level,null,a,b,s,null);var built=data.index.roads.get(road.id());
  h.assertTrue(!built.record.settings().options().streetscape().raisedSpans().isEmpty(),"derived automatic bridge spans reach authoritative record");
  h.assertTrue(built.mesh.settings().equals(built.record.settings()),"render and collision index carries the same classified settings");
  var ground=RoadStructures.sample(built.mesh,20);var raised=RoadStructures.sample(built.mesh,72);
  h.assertTrue(!RoadStreetscape.raised(built.mesh,ground)&&RoadStreetscape.raised(built.mesh,raised),"mixed road retains ground and bridge sections");
  h.assertTrue(built.record.structures().stream().anyMatch(p->base(p)&&Math.abs(RoadQueries.horizontal(built.mesh,p.a()).lateral())<.1),"bridge gets central lamps");
  h.assertTrue(built.record.structures().stream().filter(p->p.material()==Material.OAK_LOG).allMatch(p->RoadQueries.horizontal(built.mesh,p.a()).sample().distance()<51),"trees stay on ground section");
  var saved=data.save(new CompoundTag());var restored=RoadData.load(saved).index.roads.get(road.id());
  h.assertTrue(restored.record.settings().equals(built.record.settings()),"bridge ranges preserved on reload");h.assertTrue(restored.record.structures().equals(built.record.structures()),"all lamp, grate, tree geometry preserved on reload");
  h.assertTrue(RoadProfile.layout(restored.mesh,RoadStructures.sample(restored.mesh,72)).catalog().median()==RoadProfile.Median.RAIL,"client-rebuilt bridge layout uses rail median");
  var paint=RoadSurface.build(restored.mesh,List.of(),List.of()).markings();
  h.assertTrue(paint.stream().filter(f->f.color()==0xFAC136).flatMap(f->f.points().stream()).noneMatch(p->p.x()>x+53),"yellow center lines absent over elevated portion after reload");
  data.connect(level,null,a,b,s,road.id());var again=data.index.roads.get(road.id());h.assertTrue(again.record.settings().options().streetscape().raisedSpans().equals(built.record.settings().options().streetscape().raisedSpans()),"rebuild terrain probes do not treat SR slabs as ground");
  data.remove(level,null,road.id());h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_hotfix371",timeoutTicks=12000)
 public static void bridgeGreenSeparationAndLampRows(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);int x=130000,z=130000;
  var a=Revision32GameTests.marker(h,x,16,z,-90);var b=Revision32GameTests.marker(h,x+96,16,z,-90);
  var o=RoadProfile.Options.DEFAULT.cycleFinish(RoadProfile.Options.CycleFinish.GREEN).sidewalk(RoadSidewalks.Config.DEFAULT.enabled(true)).streetscape(RoadStreetscape.Config.DEFAULT.separator(RoadStreetscape.Separator.GREEN).walkLamps(true).planting(RoadStreetscape.Planting.FLOWERS));
  var s=new Settings(Mode.STRAIGHT,Style.O4_GREEN,RoadProfile.width(Style.O4_GREEN,o,4),1,.4,90).structure(Structure.BRIDGE).options(o);
  var road=data.connect(level,null,a,b,s,null);var built=data.index.roads.get(road.id());
  h.assertTrue(!built.record.settings().options().streetscape().walkLamps(),"bridge disables the separate pedestrian row");
  h.assertTrue(built.record.structures().stream().noneMatch(p->p.material()==Material.GREEN||p.material()==Material.SOIL||p.material()==Material.FLOWER_PINK),"median and cycle separator greenery become barriers on bridge");
  var feet=built.record.structures().stream().filter(Hotfix371GameTests::base).toList();h.assertTrue(feet.size()>=9&&feet.size()%3==0,"two exterior main rows and central row coexist");
  for(var foot:feet){var q=RoadQueries.horizontal(built.mesh,foot.a());h.assertTrue(Math.abs(q.lateral())<.1||Math.abs(Math.abs(q.lateral())-(q.sample().halfWidth()+.65))<1e-6,"bridge light at requested median or sidewalk-inner location");}
  var saved=RoadData.load(data.save(new CompoundTag())).index.roads.get(road.id()).record;h.assertTrue(saved.structures().equals(built.record.structures()),"bridge lamps survive world reload");
  data.remove(level,null,road.id());h.succeed();
 }
}
