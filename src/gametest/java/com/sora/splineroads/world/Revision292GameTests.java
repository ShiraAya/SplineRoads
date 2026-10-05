package com.sora.splineroads.world;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder("splineroads_revision292") @PrefixGameTestTemplate(false)
public final class Revision292GameTests {
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void sixWayMissingMarkersCanBeUpdated(GameTestHelper h) {
    var level=h.getLevel();var data=RoadData.get(level);long[] points=new long[6];
    double[] angles={0,180,60,240,120,300};
    for(int i=0;i<6;i++){double a=Math.toRadians(angles[i]);points[i]=Revision28GameTests.marker(h,20000+(int)Math.round(640*Math.cos(a)),12,20000+(int)Math.round(640*Math.sin(a))).asLong();}
    var t=Interchanges.payload(level,null,points,null);
    for(String key:List.of("Main1","Main2","Main3"))t.put(key,RoadRecord.writeSettings(Revision24GameTests.settings(Style.O2_YELLOW)));
    t.put("Options",Interchanges.write(new InterchangePlanner.Options(InterchangePlanner.Preset.DIRECTIONAL_SIX,false,1,80,16,4,2,0,true,false,0)));
    var previous=new HashSet<>(data.interchanges.keySet());Interchanges.build(level,null,t);
    UUID id=Revision272GameTests.group(data,previous);var saved=data.interchanges.get(id);points=saved.getLongArray("Points");
    var a=BlockPos.of(points[0]);var b=BlockPos.of(points[5]);
    level.setBlock(a,Blocks.AIR.defaultBlockState(),2);level.setBlock(b,Blocks.AIR.defaultBlockState(),2);
    var before=data.save(new CompoundTag());var preview=Interchanges.payload(level,null,points,id);
    h.assertTrue(before.equals(data.save(new CompoundTag()))&&level.getBlockState(a).isAir()&&level.getBlockState(b).isAir(),"opening legacy interchange is read-only");
    h.assertTrue(preview.getList("Nodes",10).equals(saved.getList("Nodes",10)),"both missing anchors come from exact server-saved descriptor");
    level.setBlock(b,Blocks.CHEST.defaultBlockState(),2);boolean blocked=false;
    try{Interchanges.build(level,null,preview);}catch(IllegalArgumentException e){blocked=e.getMessage().contains("原位置被占用");}
    h.assertTrue(blocked&&level.getBlockState(a).isAir()&&before.equals(data.save(new CompoundTag())),"blocked recovery does not partly recreate the other endpoint");
    h.assertTrue(level.getBlockState(b).is(Blocks.CHEST),"recovery preserves occupied block");level.setBlock(b,Blocks.AIR.defaultBlockState(),2);
    Interchanges.build(level,null,preview);
    for(long key:data.interchanges.get(id).getLongArray("Points"))h.assertTrue(level.getBlockEntity(BlockPos.of(key)) instanceof NodeEntity,"every selected marker is restored after commit");
    h.assertTrue(data.index.roads.values().stream().filter(r->id.equals(r.record.assembly())).noneMatch(RoadIndex.Built::rasterized),"six-way edit releases full collision raster");
    var reload=RoadData.load(data.save(new CompoundTag()));h.assertTrue(reload.interchanges.get(id).getList("Nodes",10).equals(data.interchanges.get(id).getList("Nodes",10)),"repaired descriptor reloads");
    Interchanges.remove(level,null,id);h.assertTrue(RoadWorkChunks.heldCount(level)==0,"restoration releases chunk tickets");h.succeed();
  }

  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void legacyChunkRepairRemainsLocalAndCollisionExact(GameTestHelper h) {
    var level=h.getLevel();var data=RoadData.get(level);var a=Revision28GameTests.marker(h,23000,12,23000);var b=Revision28GameTests.marker(h,23400,24,23020);
    var s=Revision28GameTests.road(Style.O2_YELLOW,Structure.BRIDGE,RoadInfrastructure.Config.DEFAULT.gantry(RoadInfrastructure.Gantry.OFF));
    var r=data.connect(level,null,a,b,s,null);var built=data.index.roads.get(r.id());
    h.assertTrue(!built.rasterized(),"normal build releases full raster");
    var expected=new HashMap<>(built.cells);built.releaseEditRaster();
    var tag=r.save();tag.putInt("BuildVersion",16);var old=RoadIndex.Built.loading(RoadRecord.load(tag));data.index.put(old);
    ChunkPos chunk=new ChunkPos(a.offset(32,0,0));var cells=old.prepareCollision().cellsInChunk(chunk.x,chunk.z);
    Set<RoadRaster.Cell> wanted=new HashSet<>();for(long key:expected.keySet()){var p=BlockPos.of(key);if(new ChunkPos(p).equals(chunk))wanted.add(new RoadRaster.Cell(p.getX(),p.getY(),p.getZ()));}
    h.assertTrue(cells.equals(wanted),"chunk migration covers precisely the old deck and structures");
    var lost=cells.stream().filter(c->level.getBlockState(new BlockPos(c.x(),c.y(),c.z())).getBlock()!=SplineRoads.NODE.get()).findFirst().orElseThrow();
    var pos=new BlockPos(lost.x(),lost.y(),lost.z());level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);data.repairChunk(level,chunk);
    h.assertTrue(RoadBlocks.isCollider(level.getBlockState(pos))&&!old.rasterized(),"legacy collision repaired without whole-road rasterization");
    var snapshot=data.save(new CompoundTag());data.repairChunk(level,chunk);h.assertTrue(snapshot.equals(data.save(new CompoundTag())),"walking same chunk again makes no repeated writes");
    for(var c:cells){var p=new BlockPos(c.x(),c.y(),c.z());h.assertTrue(new HashSet<>(old.boxes(p)).equals(new HashSet<>(expected.get(p.asLong()))),"local collision is exact after release");}
    data.remove(level,null,r.id());h.succeed();
  }
}
