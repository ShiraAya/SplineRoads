package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("splineroads_revision233") @PrefixGameTestTemplate(false)
public final class Revision233GameTests {
  static BlockPos node(GameTestHelper h,int x,int y,int z){var p=new BlockPos(x,y,z);h.getLevel().setBlock(p,SplineRoads.NODE.get().defaultBlockState(),3);((NodeEntity)h.getLevel().getBlockEntity(p)).owner=new UUID(0,0);return p;}
  static Settings settings(Style style){var options=RoadProfile.Options.DEFAULT.extras(false,false,true).sidewalk(RoadSidewalks.Config.DEFAULT.enabled(true));return new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,options,4),1,.35,90).structure(Structure.GROUND).options(options);}
  @GameTest(template="empty",templateNamespace="splineroads_revision233",timeoutTicks=2400)
  public static void previewAndBuildShareUnequalContinuationSeams(GameTestHelper h){
    var l=h.getLevel();var d=RoadData.get(l);var c=node(h,61000,90,61000);var a=node(h,61140,90,61000);var b=node(h,61000,90,61140);var e=node(h,61280,90,61000);
    var narrow=settings(Style.O4_YELLOW);var wide=settings(Style.O6_RAIL);
    var first=d.connect(l,null,c,a,narrow,null);var second=d.connect(l,null,c,b,narrow,null,false,true);
    UUID id=UUID.randomUUID();var cmd=new CompoundTag();cmd.putUUID("Id",id);cmd.putLong("A",a.asLong());cmd.putLong("B",e.asLong());
    cmd.put("StartNode",RoadRecord.writeNode(((NodeEntity)l.getBlockEntity(a)).constructionNode()));cmd.put("EndNode",RoadRecord.writeNode(((NodeEntity)l.getBlockEntity(e)).constructionNode()));cmd.put("Settings",RoadRecord.writeSettings(wide));
    AutoJunctions.enrich(cmd,d,a,e,null);d.jointPayload(cmd,a,e,null);
    var preview=AutoJunctions.preview(cmd);
    var continuation=d.connect(l,null,a,e,wide,null);
    var before=d.index.roads.get(first.id());var after=d.index.roads.get(continuation.id());
    h.assertTrue(Math.abs(before.mesh.last().halfWidth()-after.mesh.first().halfWidth())<1e-7,"junction street and continuation share actual width");
    for(var planned:preview.roads()){
      var built=d.index.roads.get(planned.id().equals(id)?continuation.id():planned.id());
      h.assertTrue(built!=null&&planned.mesh().samples().stream().allMatch(sample -> RoadQueries.horizontal(built.mesh,sample.center()).horizontalDistance()<1e-6),"successful preview centerline is actually buildable (free-end caps may extend it)");
    }
    d.connect(l,null,c,a,wide,first.id());d.connect(l,null,a,e,narrow,continuation.id());
    before=d.index.roads.get(first.id());after=d.index.roads.get(continuation.id());h.assertTrue(Math.abs(before.mesh.last().halfWidth()-after.mesh.first().halfWidth())<1e-7,"edits re-normalize both sides");
    var reload=RoadData.load(d.save(new CompoundTag()));h.assertTrue(reload.index.roads.get(first.id()).mesh.samples().equals(before.mesh.samples()),"seam shape persists");
    d.remove(l,null,continuation.id());d.remove(l,null,second.id());d.remove(l,null,first.id());h.assertTrue(RoadWorkChunks.heldCount(l)==0,"all work chunks released");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision233",timeoutTicks=1600)
  public static void deferredReloadCollisionAndWatch(GameTestHelper h){
    var l=h.getLevel();var d=RoadData.get(l);var a=node(h,-62000,90,-62000);var b=node(h,-61900,104,-61965);
    var road=d.connect(l,null,a,b,settings(Style.O4_YELLOW),null);var built=d.index.roads.get(road.id());var saved=d.save(new CompoundTag());
    var loaded=RoadData.load(saved);var cold=loaded.index.roads.get(road.id());h.assertTrue(!cold.rasterized(),"world entry does not rasterize saved decks");
    loaded.save(new CompoundTag());loaded.repairChunk(l,new ChunkPos(a));h.assertTrue(!cold.rasterized(),"saving and watching modern chunks keep collision deferred");
    int n=0;for(long key:built.cells.keySet())if(n++%23==0){var p=BlockPos.of(key);h.assertTrue(!Shapes.joinIsNotEmpty(d.index.shape(p),loaded.index.shape(p),BooleanOp.NOT_SAME),"lazy and eager collision occupy exactly same volume");}
    h.assertTrue(!cold.rasterized(),"nearby collision does not force whole-road raster");
    h.assertTrue(cold.cells.equals(built.cells),"edit materialization retains exact deck cells");h.assertTrue(cold.rasterized(),"full materialization available for edit transactions");
    long key=built.cells.keySet().iterator().next();loaded.index.shape(BlockPos.of(key));loaded.index.remove(road.id());h.assertTrue(loaded.index.shape(BlockPos.of(key)).isEmpty(),"remove invalidates cached collision only in affected chunks");
    d.remove(l,null,road.id());h.assertTrue(RoadWorkChunks.heldCount(l)==0,"slope work chunks released");h.succeed();
  }
}
