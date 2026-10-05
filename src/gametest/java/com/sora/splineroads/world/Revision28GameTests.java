package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadInfrastructure.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder("splineroads_revision28") @PrefixGameTestTemplate(false)
public final class Revision28GameTests {
  static Settings road(Style style,Structure kind,Config c){return new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.4,90).structure(kind).options(RoadProfile.Options.DEFAULT.infrastructure(c));}
  static BlockPos marker(GameTestHelper h,int x,int y,int z){var p=new BlockPos(x,y,z);Revision27GameTests.marker(h.getLevel(),p);return p;}
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void completeTunnelLifecycle(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);
    // A real solid hill: clearing only the old four-block headroom would leave stone in the roof space.
    for(int x=8000;x<=8040;x++)for(int z=7991;z<=8010;z++)for(int y=1;y<=16;y++){var p=new BlockPos(x,y,z);level.getChunkAt(p);level.setBlock(p,Blocks.STONE.defaultBlockState(),2);}
    var a=marker(h,8000,2,8000);var b=marker(h,8040,2,8000);
    var s=road(Style.O2_YELLOW,Structure.TUNNEL,Config.DEFAULT.tunnel(Tunnel.ARCH));
    var record=data.connect(level,null,a,b,s,null);var built=data.index.roads.get(record.id());
    h.assertTrue(level.getBlockState(new BlockPos(8020,7,8000)).isAir(),"full-height tunnel is excavated");
    h.assertTrue(!built.shellCells.isEmpty()&&!built.lightCells.isEmpty(),"shell and lights own actual world cells");
    for(long key:built.shellCells){var p=BlockPos.of(key);var state=level.getBlockState(p);h.assertTrue(RoadBlocks.isCollider(state)&&state.getValue(RoadBlocks.Road.SEALED),"wall and roof block skylight");h.assertTrue(!state.propagatesSkylightDown(level,p),"sealed shell stops sky propagation");}
    var lamp=BlockPos.of(built.lightCells.stream().findFirst().orElseThrow());h.assertTrue(level.getBlockState(lamp).getLightEmission(level,lamp)==15,"lamps emit real level 15 light");
    var loaded=RoadData.load(data.save(new CompoundTag())).index.roads.get(record.id());
    h.assertTrue(loaded.record.structures().equals(built.record.structures())&&loaded.record.settings().options().infrastructure().equals(s.options().infrastructure()),"roof frames, lamp materials and parameters reload exactly");loaded.cells.size();h.assertTrue(loaded.shellCells.equals(built.shellCells),"deferred loading rebuilds opaque shell cells");
    var litChunks=Set.copyOf(built.chunks);for(long key:litChunks){var chunk=new net.minecraft.world.level.ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,true);}
    h.runAfterDelay(60,()->{
      try{
      System.out.println("Tunnel lighting: "+lamp+" source="+level.getBrightness(LightLayer.BLOCK,lamp)+" ready="+level.getChunkAt(lamp).isLightCorrect()+" centre="+level.getBrightness(LightLayer.BLOCK,new BlockPos(8020,4,8000)));
      h.assertTrue(java.util.Arrays.stream(net.minecraft.core.Direction.values()).anyMatch(dir->level.getBrightness(LightLayer.BLOCK,lamp.relative(dir))>0),"lamps illuminate neighboring cells");
      h.assertTrue(level.getBrightness(LightLayer.BLOCK,new BlockPos(8020,4,8000))>0,"ceiling strips illuminate the centre of the driving corridor");
      var oldLights=Set.copyOf(built.lightCells);var next=s.options(s.options().infrastructure(s.options().infrastructure().tunnel(Tunnel.BOX).headroom(8)));
      data.connect(level,null,a,b,next,record.id());var updated=data.index.roads.get(record.id());
      for(long key:oldLights)if(!updated.lightCells.contains(key)){var state=level.getBlockState(BlockPos.of(key));h.assertTrue(!RoadBlocks.isCollider(state)||!state.getValue(RoadBlocks.Road.LIT),"editing height removes old lamps");}
      var occupied=new HashSet<>(updated.cells.keySet());occupied.addAll(built.cells.keySet());
      data.remove(level,null,record.id());h.assertTrue(level.getBlockState(new BlockPos(8020,7,8000)).is(Blocks.STONE),"removal restores excavated rock");
      for(long key:occupied)h.assertTrue(!RoadBlocks.isCollider(level.getBlockState(BlockPos.of(key))),"removal leaves no roof, wall or lamp collision");
      h.assertTrue(RoadWorkChunks.heldCount(level)==0,"tickets released");h.succeed();
      }finally{for(long key:litChunks){var chunk=new net.minecraft.world.level.ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,false);}}
    });
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void allBridgeStylesAndGantryRoundTrip(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);int z=8400;
    for(Bridge bridge:Bridge.values()){
      var a=marker(h,8000,12,z);var b=marker(h,8128,12,z);z+=96;
      var s=road(Style.H4_RAIL,Structure.BRIDGE,Config.DEFAULT.bridge(bridge).span(128));var r=data.connect(level,null,a,b,s,null);var built=data.index.roads.get(r.id());
      h.assertTrue(RoadGantry.count(built.mesh)>0&&built.record.structures().stream().anyMatch(p->p.material()==RoadStructures.Material.STEEL&&Math.abs(p.width()-.45)<1e-8&&Math.abs(p.height()-.45)<1e-8),"elevated highway gantry structure");
      h.assertTrue(built.record.structures().stream().noneMatch(p->p.material()==RoadStructures.Material.SIGN_GREEN||p.material()==RoadStructures.Material.SIGN_BLUE||p.material()==RoadStructures.Material.SIGN_WHITE),"retired automatic sign panels stay removed");
      h.assertTrue(built.record.structures().stream().anyMatch(p->p.pier()&&p.height()>10),"bridge foundations");
      var loaded=RoadData.load(data.save(new CompoundTag())).index.roads.get(r.id());h.assertTrue(loaded.record.structures().equals(built.record.structures()),"bridge model reload");
      var changed=s.options(s.options().infrastructure(s.options().infrastructure().gantry(Gantry.OFF)));data.connect(level,null,a,b,changed,r.id());
      h.assertTrue(data.index.roads.get(r.id()).record.structures().stream().noneMatch(p->p.material()==RoadStructures.Material.STEEL&&Math.abs(p.width()-.45)<1e-8&&Math.abs(p.height()-.45)<1e-8),"gantry toggle takes effect on existing bridge");
      data.remove(level,null,r.id());
    }
    h.assertTrue(RoadWorkChunks.heldCount(level)==0,"tickets released");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void overpassAvoidsLowerRoad(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);
    var a=marker(h,8400,12,9200);var b=marker(h,8496,12,9200);
    var c=marker(h,8448,2,9152);var d=marker(h,8448,2,9248);
    for(var p:List.of(c,d)){var n=(NodeEntity)level.getBlockEntity(p);n.apply(new Node(n.node().position(),0,0));}
    var lower=data.connect(level,null,c,d,road(Style.O2_YELLOW,Structure.GROUND,Config.DEFAULT.gantry(Gantry.OFF)),null);
    var upper=data.connect(level,null,a,b,road(Style.O4_YELLOW,Structure.BRIDGE,Config.DEFAULT.bridge(Bridge.OVERPASS)),null);
    var mesh=data.index.roads.get(lower.id()).mesh;var up=data.index.roads.get(upper.id());
    for(var part:up.record.structures())if(part.pier()&&part.height()>10)h.assertTrue(!RoadQueries.contains(mesh,part.a(),100,1.5),"portal pier does not block the lower road");
    var snapshot=data.save(new CompoundTag());boolean rejected=false;
    try{data.connect(level,null,c,d,road(Style.O2_YELLOW,Structure.TUNNEL,Config.DEFAULT.headroom(10)),lower.id());}catch(IllegalArgumentException e){rejected=true;}
    h.assertTrue(rejected&&snapshot.equals(data.save(new CompoundTag())),"conflicting tunnel upgrade is rejected atomically");
    data.remove(level,null,upper.id());data.remove(level,null,lower.id());h.assertTrue(RoadWorkChunks.heldCount(level)==0,"tickets released");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void bridgeAndTunnelConnectToOrdinaryRoad(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);
    var a=marker(h,8600,12,10000);var b=marker(h,8664,12,10000);var c=marker(h,8728,12,10000);
    var bridge=road(Style.O2_YELLOW,Structure.BRIDGE,Config.DEFAULT.bridge(Bridge.BEAM).gantry(Gantry.OFF));
    var first=data.connect(level,null,a,b,bridge,null);
    var second=data.connect(level,null,b,c,road(Style.O2_YELLOW,Structure.GROUND,Config.DEFAULT.gantry(Gantry.OFF)),null);
    h.assertTrue(data.index.roads.get(first.id()).mesh.last().center().distance(data.index.roads.get(second.id()).mesh.first().center())<.001,"bridge-to-surface seam remains continuous");
    data.connect(level,null,a,b,road(Style.O2_YELLOW,Structure.TUNNEL,Config.DEFAULT),first.id());
    h.assertTrue(!data.index.roads.get(first.id()).shellCells.isEmpty()&&data.index.roads.get(second.id()).record.settings().structure()==Structure.GROUND,"tunnel mouth connects without enclosing the surface continuation");
    data.remove(level,null,first.id());data.remove(level,null,second.id());h.assertTrue(RoadWorkChunks.heldCount(level)==0,"connection tests release work chunks");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void tunnelProtectsContainersAndLegacySettings(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var a=marker(h,8400,2,9600);var b=marker(h,8464,2,9600);
    var s=road(Style.O2_YELLOW,Structure.TUNNEL,Config.DEFAULT);var chest=new BlockPos(8432,7,9600);level.setBlock(chest,Blocks.CHEST.defaultBlockState(),2);var before=data.save(new CompoundTag());boolean rejected=false;
    try{data.connect(level,null,a,b,s,null);}catch(IllegalArgumentException e){rejected=e.getMessage().contains("方块实体");}
    h.assertTrue(rejected&&level.getBlockState(chest).is(Blocks.CHEST)&&before.equals(data.save(new CompoundTag())),"container in excavation is preserved without partial construction");
    var legacy=RoadRecord.writeSettings(s.structure(Structure.AUTO));legacy.remove("Infrastructure");h.assertTrue(RoadRecord.readSettings(legacy).options().infrastructure().equals(Config.DEFAULT.grade(0).autoSpan(false)),"old 0.27 settings retain compatible legacy grading and span policy");
    h.assertTrue(RoadWorkChunks.heldCount(level)==0,"tickets released after rejection");h.succeed();
  }
}
