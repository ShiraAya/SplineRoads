package com.sora.splineroads.world;

import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;

@GameTestHolder("splineroads_revision261") @PrefixGameTestTemplate(false)
public final class Revision261GameTests {
  private record Fixture(RoadData data, BlockPos source, BlockPos target, BlockPos end,
                         Node next, RoadIndex.Built road, RoadData.NodeMove move) {}
  private static NodeEntity marker(ServerLevel level,BlockPos p){
    level.getChunkAt(p);level.setBlock(p,SplineRoads.NODE.get().defaultBlockState(),2);
    NodeEntity n=(NodeEntity)level.getBlockEntity(p);n.owner=new UUID(0,0);n.heightExplicit=true;
    n.apply(new Node(new V(p.getX()+.5,p.getY()+.25,p.getZ()+.5),-90,0));return n;
  }
  private static Fixture fixture(GameTestHelper h,int z,BlockState obstruction){
    var level=h.getLevel();var data=RoadData.get(level);
    BlockPos target=new BlockPos(-346,2,z),source=target.offset(-12,0,0),end=target.offset(32,0,0);
    NodeEntity old=marker(level,source),last=marker(level,end);
    level.getChunkAt(target);level.setBlock(target,obstruction,2);
    Node next=new Node(new V(target.getX()+.5,2.25,target.getZ()+.5),-90,0);
    Settings s=new Settings(Mode.STRAIGHT,Style.O2_YELLOW,Style.O2_YELLOW.defaultWidth(),1,.4,90).structure(Structure.GROUND);
    Mesh mesh=RoadPlanner.plan(RoadPlanner.Hint.free(next),RoadPlanner.Hint.free(last.constructionNode()),s).mesh();
    RoadRecord r=new RoadRecord(UUID.randomUUID(),new UUID(0,0),target,end,next,last.constructionNode(),s,false,4).alignment(UUID.randomUUID(),mesh);
    return new Fixture(data,source,target,end,next,new RoadIndex.Built(r),new RoadData.NodeMove(source,target,next,old.saveWithoutMetadata()));
  }
  private static void build(GameTestHelper h,Fixture f){
    f.data.replaceAssembly(h.getLevel(),null,List.of(f.road),Set.of(),Set.of(f.source,f.end),List.of(f.move));
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision261",timeoutTicks=1200)
  public static void terrainAtMovedEndpointUsesConstructionClearing(GameTestHelper h){
    int z=111;
    for(BlockState state:List.of(Blocks.GRASS_BLOCK.defaultBlockState(),Blocks.DIRT.defaultBlockState(),Blocks.STONE.defaultBlockState())){
      Fixture f=fixture(h,z,state);z+=64;
      h.assertTrue(f.road.cells.containsKey(f.target.asLong()),"obstruction is inside the new road body");
      build(h,f);
      h.assertTrue(h.getLevel().getBlockEntity(f.target) instanceof NodeEntity,"planned terrain is replaced by the fitted marker");
      h.assertTrue(((NodeEntity)h.getLevel().getBlockEntity(f.target)).constructionNode().equals(f.next),"exact fitted coordinates retained");
      h.assertTrue(h.getLevel().getBlockState(f.source).isAir(),"old marker is removed only after successful construction");
      h.assertTrue(f.data.index.roads.containsKey(f.road.record.id()),"real road transaction committed");
      CompoundTag saved=f.data.save(new CompoundTag());
      h.assertTrue(RoadData.load(saved).index.roads.containsKey(f.road.record.id()),"committed road survives save/load");
      boolean retained=false;
      for(var entry:saved.getList("OriginalPalette",10)){CompoundTag t=(CompoundTag)entry;
        if(Arrays.stream(t.getLongArray("Positions")).anyMatch(pos->pos==f.target.asLong()))retained=t.getCompound("State").equals(net.minecraft.nbt.NbtUtils.writeBlockState(state));}
      h.assertTrue(retained,"original terrain remains in the restoration journal");
      h.assertTrue(RoadWorkChunks.heldCount(h.getLevel())==0,"construction releases work chunks");
    }
    h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision261",timeoutTicks=1200)
  public static void containerTargetRejectsWithoutPartialWrites(GameTestHelper h){
    Fixture f=fixture(h,400,Blocks.CHEST.defaultBlockState());
    var chest=(ChestBlockEntity)h.getLevel().getBlockEntity(f.target);chest.setItem(0,new ItemStack(Items.DIAMOND,7));
    BlockPos terrain=f.target.offset(4,0,0);h.getLevel().setBlock(terrain,Blocks.DIRT.defaultBlockState(),2);
    CompoundTag before=f.data.save(new CompoundTag());boolean rejected=false;
    try{build(h,f);}catch(IllegalArgumentException e){rejected=e.getMessage().contains("端点目标被占用")&&e.getMessage().contains("minecraft:chest");}
    h.assertTrue(rejected,"container at endpoint is a real obstruction");
    h.assertTrue(h.getLevel().getBlockEntity(f.target)==chest&&chest.getItem(0).getCount()==7,"container and contents retained");
    h.assertTrue(h.getLevel().getBlockEntity(f.source) instanceof NodeEntity,"old endpoint retained on failure");
    h.assertTrue(h.getLevel().getBlockState(terrain).is(Blocks.DIRT),"planned corridor was not partially cleared");
    h.assertTrue(before.equals(f.data.save(new CompoundTag())),"index and restoration journal unchanged on rejection");
    h.assertTrue(RoadWorkChunks.heldCount(h.getLevel())==0,"failed transaction releases work chunks");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision261",timeoutTicks=1200)
  public static void unbreakableTargetRemainsProtected(GameTestHelper h){
    Fixture f=fixture(h,500,Blocks.BEDROCK.defaultBlockState());boolean rejected=false;
    try{build(h,f);}catch(IllegalArgumentException e){rejected=e.getMessage().contains("不可破坏");}
    h.assertTrue(rejected,"bedrock cannot be cleared for an endpoint");
    h.assertTrue(h.getLevel().getBlockState(f.target).is(Blocks.BEDROCK),"bedrock retained");
    h.assertTrue(h.getLevel().getBlockEntity(f.source) instanceof NodeEntity,"source retained");
    h.assertTrue(!f.data.index.roads.containsKey(f.road.record.id()),"no partial road committed");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision261",timeoutTicks=1200)
  public static void solidTargetOutsideConstructionRemainsBlocked(GameTestHelper h){
    Fixture f=fixture(h,600,Blocks.STONE.defaultBlockState());boolean rejected=false;
    try{f.data.replaceAssembly(h.getLevel(),null,List.of(),Set.of(),Set.of(f.source),List.of(f.move));}
    catch(IllegalArgumentException e){rejected=e.getMessage().contains("端点目标被占用")&&e.getMessage().contains("minecraft:stone");}
    h.assertTrue(rejected,"endpoint alone cannot erase a solid block outside road clearing");
    h.assertTrue(h.getLevel().getBlockState(f.target).is(Blocks.STONE),"unplanned solid block retained");
    h.assertTrue(h.getLevel().getBlockEntity(f.source) instanceof NodeEntity,"source retained");h.succeed();
  }
}
