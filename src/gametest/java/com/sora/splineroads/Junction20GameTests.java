package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.world.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("splineroads_junction20")
@PrefixGameTestTemplate(false)
public final class Junction20GameTests {
  private static BlockPos node(GameTestHelper h,int x,int z) {
    var p=new BlockPos(x,90,z);h.getLevel().setBlock(p,SplineRoads.NODE.get().defaultBlockState(),3);
    var n=(NodeEntity)h.getLevel().getBlockEntity(p);n.owner=new UUID(0,0);return p;
  }
  @GameTest(template="empty",templateNamespace="splineroads_junction20",timeoutTicks=600)
  public static void buildEditPersistAndDelete(GameTestHelper h) {
    var level=h.getLevel();var data=RoadData.get(level);
    long[] points={node(h,45000,45000).asLong(),node(h,45090,45000).asLong(),node(h,45000,45090).asLong(),node(h,44910,45000).asLong(),node(h,45000,44910).asLong()};
    var end=node(h,45140,45000);var outside=data.connect(level,null,BlockPos.of(points[1]),end,Settings.defaults(),null);
    var command=Junctions.payload(level,null,points,null);var spec=command.getCompound("Spec");spec.putString("Control","SIGNALS");
    Junctions.build(level,null,command);
    var first=data.index.roads.values().stream().filter(r->r.record.junction()!=null).findFirst().orElseThrow();UUID id=first.record.assembly();
    var planned=JunctionPlanner.plan(JunctionCodec.read(spec));
    var records=data.index.roads.values().stream().filter(r->id.equals(r.record.assembly())).toList();
    h.assertTrue(records.size()==planned.pieces().size(),"all preview pieces constructed");
    for(var r:records){var copy=RoadRecord.load(r.record.save());h.assertTrue(copy.junction().equals(r.record.junction()),"custom lanes and piece identity persist");h.assertTrue(copy.mesh().equals(r.mesh),"load recreates exact preview mesh");h.assertTrue(!r.cells.isEmpty(),"physical deck collision created");}
    var loaded=RoadData.load(data.save(new CompoundTag()));h.assertTrue(loaded.index.roads.size()==data.index.roads.size(),"world reload preserves junction and outside road");
    command=Junctions.payload(level,null,points,id);spec=command.getCompound("Spec");spec.putString("Kind","ROUNDABOUT");spec.putInt("RingLanes",2);Junctions.build(level,null,command);
    var reloaded=Junctions.payload(level,null,points,id);h.assertTrue(reloaded.getCompound("Spec").getString("Kind").equals("ROUNDABOUT"),"whole junction editable into roundabout");
    h.assertTrue(data.index.roads.containsKey(outside.id()),"editing keeps external road");
    var bad=reloaded.copy();bad.getCompound("Spec").putDouble("Island",48);int before=data.index.roads.size();
    // A protected block within the road must reject atomically, preserving the previous junction.
    var obstruction=BlockPos.of(points[1]).offset(-25,1,0);level.setBlock(obstruction,net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(),3);
    boolean rejected=false;try{Junctions.build(level,null,bad);}catch(IllegalArgumentException e){rejected=true;}
    h.assertTrue(rejected,"blocked construction rejected");h.assertTrue(data.index.roads.size()==before,"failed edit leaves complete old junction");
    level.setBlock(obstruction,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
    var part=data.index.roads.values().stream().filter(r->id.equals(r.record.assembly())).findFirst().orElseThrow();data.remove(level,null,part.record.id());
    h.assertTrue(data.index.roads.values().stream().noneMatch(r->id.equals(r.record.assembly())),"single removal selection deletes the entire junction");
    h.assertTrue(data.index.roads.containsKey(outside.id()),"deleting junction keeps external road");
    for(long p:points)h.assertTrue(level.getBlockEntity(BlockPos.of(p)) instanceof NodeEntity,"selected endpoint preserved");
    data.remove(level,null,outside.id());h.assertTrue(RoadWorkChunks.heldCount(level)==0,"all work chunks released");h.succeed();
  }
}
