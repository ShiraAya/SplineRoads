package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_hotfix432") @PrefixGameTestTemplate(false)
public final class Build432GameTests {
 // Raised deterministic test surface, above generated structures. No production protection disabled.
 static void floor(GameTestHelper h,int x1,int x2,int z1,int z2){var l=h.getLevel();for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++){var p=new BlockPos(x,80,z);l.getChunkAt(p);l.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);}}
 @GameTest(batch="splineroads_hotfix432",template="empty",templateNamespace="splineroads_hotfix432",timeoutTicks=12000)
 public static void addLaneActualBuildSaveDelete(GameTestHelper h){
  System.setProperty("sr.profile","true");var l=h.getLevel();var d=RoadData.get(l);floor(h,91890,92030,91980,92620);
  var ta=Revision32GameTests.marker(h,92000,90,92000,0);var tb=Revision32GameTests.marker(h,92000,90,92600,0);
  var sa=Revision32GameTests.marker(h,91920,82,92040,0);var sb=Revision32GameTests.marker(h,91920,82,92220,0);
  var target=d.connect(l,null,ta,tb,Revision32GameTests.road(Style.O3_ONE,Structure.AUTO),null);
  var source=d.connect(l,null,sa,sb,Revision32GameTests.road(Style.O2_ONE,Structure.AUTO),null);
  var a=Build429GameTests.point(d,source,60,0);var b=Build429GameTests.point(d,target,400,2);
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.ADD,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  for(int repeat=0;repeat<4;repeat++){
  long start=System.nanoTime();var r=LaneRamps.generate(d,LaneTopology.records(d),UUID.randomUUID(),source.owner(),new LanePoints.Link(a,b,options,null));
  double planMs=(System.nanoTime()-start)/1e6;start=System.nanoTime();r=preview(h,d,r,source,target);double previewMs=lastPreviewMs;start=System.nanoTime();LaneRamps.build(d,l,null,r);
  double buildMs=(System.nanoTime()-start)/1e6;report("ADD",repeat,planMs,previewMs,buildMs,d.index.roads.get(r.id()));
  h.assertTrue(d.index.roads.containsKey(r.id())&&d.index.roads.get(r.id()).cells.size()>0,"ADD road and real collision committed");
  h.assertTrue(LaneSections.live(d.index.roads.get(target.id()).mesh,500).forward()==4,"ADD 3->4 actual host");
  var saved=RoadData.load(d.save(new CompoundTag()));h.assertTrue(LaneSections.live(saved.index.roads.get(target.id()).mesh,500).forward()==4,"Mojang NBT load keeps ADD");
  d.remove(l,null,r.id());h.assertTrue(!d.index.roads.containsKey(r.id()),"ramp delete");h.assertTrue(LaneSections.live(d.index.roads.get(target.id()).mesh,500).forward()==3,"delete restores 3 host lanes");
  }
  System.out.println("BUILD432 ADD_SAVE_DELETE_PASS");h.succeed();
 }
 @GameTest(batch="splineroads_hotfix432",template="empty",templateNamespace="splineroads_hotfix432",timeoutTicks=12000)
 public static void temporaryMergeWithHighwayFurniture(GameTestHelper h){
  System.setProperty("sr.profile","true");var l=h.getLevel();var d=RoadData.get(l);floor(h,94720,95280,94980,95620);
  var ta=Revision32GameTests.marker(h,95000,100,95000,0);var tb=Revision32GameTests.marker(h,95000,100,95600,0);
  var sa=Revision32GameTests.marker(h,94750,88,95240,-90);var sb=Revision32GameTests.marker(h,95250,88,95240,-90);
  var target=d.connect(l,null,ta,tb,Revision32GameTests.road(Style.H6_RAIL,Structure.AUTO),null);
  var source=d.connect(l,null,sa,sb,Revision32GameTests.road(Style.H6_RAIL,Structure.AUTO),null);
  var a=Build429GameTests.point(d,source,80,3);var b=Build429GameTests.point(d,target,420,5);
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.FLEXIBLE);
  for(int repeat=0;repeat<4;repeat++){
  long start=System.nanoTime();var r=LaneRamps.generate(d,LaneTopology.records(d),UUID.randomUUID(),source.owner(),new LanePoints.Link(a,b,options,null));
  double planMs=(System.nanoTime()-start)/1e6;start=System.nanoTime();r=preview(h,d,r,source,target);double previewMs=lastPreviewMs;start=System.nanoTime();LaneRamps.build(d,l,null,r);
  double buildMs=(System.nanoTime()-start)/1e6;report("TEMPORARY",repeat,planMs,previewMs,buildMs,d.index.roads.get(r.id()));
  h.assertTrue(d.index.roads.containsKey(r.id())&&d.index.roads.get(r.id()).cells.size()>0,"TEMPORARY/MERGE actual collision committed");
  h.assertTrue(!LaneTopology.metadata(d.index.roads.get(source.id()).record).cuts().isEmpty(),"temporary closure reserved on host");
  d.remove(l,null,r.id());h.assertTrue(LaneTopology.metadata(d.index.roads.get(source.id()).record).cuts().isEmpty(),"delete restores temporary closure");
  }
  System.out.println("BUILD432 TEMPORARY_DELETE_PASS");h.succeed();
 }
 static void report(String kind,int repeat,double plan,double preview,double build,RoadIndex.Built road){
  long signature=1;for(var cell:new TreeMap<>(road.cells).entrySet()){
    signature=31*signature+cell.getKey();for(var b:cell.getValue())for(double value:new double[]{b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1()})signature=31*signature+Double.doubleToLongBits(value);
  }
  System.out.printf(Locale.ROOT,"BENCH432 kind=%s run=%d plan=%.6f preview=%.6f build=%.6f total=%.6f cells=%d shape=%d%n",kind,repeat,plan,preview,build,plan+preview+build,road.cells.size(),signature);
 }
 static double lastPreviewMs;
 static RoadRecord preview(GameTestHelper h,RoadData data,RoadRecord ramp,RoadRecord source,RoadRecord target){
  long revision=data.index.revision();var before=data.save(new CompoundTag());
  long start=System.nanoTime();
  var result=data.previewAssembly(h.getLevel(),null,List.of(new RoadIndex.Built(ramp)),Set.of(),
      new HashSet<>(List.of(ramp.a(),ramp.b(),source.a(),source.b(),target.a(),target.b())),List.of());
  lastPreviewMs=(System.nanoTime()-start)/1e6;
  System.out.println("BUILD432 PREVIEW_PREFLIGHT_MS="+lastPreviewMs);
  h.assertTrue(revision==data.index.revision(),"preview does not mutate index revision");
  h.assertTrue(before.equals(data.save(new CompoundTag())),"preview does not commit road/NBT changes");
  var planned=result.stream().filter(b->b.record.id().equals(ramp.id())).findFirst().orElseThrow();
  h.assertTrue(!planned.cells.isEmpty(),"preview has actual collision result, not timeout or empty wireframe");
  for(var built:result){long hash=1;for(var entry:new TreeMap<>(built.cells).entrySet()){
    hash=31*hash+entry.getKey();for(var box:entry.getValue())for(double v:new double[]{box.x0(),box.y0(),box.z0(),box.x1(),box.y1(),box.z1()})hash=31*hash+Double.doubleToLongBits(v);
  }System.out.printf(Locale.ROOT,"SHAPE432 start=%s end=%s cells=%d hash=%d%n",built.record.a(),built.record.b(),built.cells.size(),hash);}
  try{System.out.println("BUILD432 RASTER_CACHE="+Arrays.toString((long[])RoadRaster.class.getMethod("savedRasterStats").invoke(null)));}catch(ReflectiveOperationException noCacheInBaseline){}
  return planned.record;
 }
}
