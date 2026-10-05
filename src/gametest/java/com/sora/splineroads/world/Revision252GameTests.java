package com.sora.splineroads.world;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
@GameTestHolder("splineroads_revision252") @PrefixGameTestTemplate(false)
public final class Revision252GameTests {
 static Settings road(Style style){return new Settings(Mode.AUTO,style,style.defaultWidth(),.25,.4,90);}
 static void node(GameTestHelper h,BlockPos p){var level=h.getLevel();level.getChunkAt(p);level.setBlock(p,SplineRoads.NODE.get().defaultBlockState(),3);var entity=(NodeEntity)level.getBlockEntity(p);entity.owner=new UUID(0,0);entity.heightExplicit=true;entity.apply(new Node(new V(p.getX()+.5,64,p.getZ()+.5),-90,0));}
 @GameTest(template="empty",templateNamespace="splineroads_revision252",timeoutTicks=1200)
 public static void highwayConnectsOrdinaryBothOrders(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);
  for(boolean reverse:new boolean[]{false,true}){
   int origin=114000+(reverse?256:0);BlockPos a=new BlockPos(origin,64,114000),b=a.offset(64,0,0),c=b.offset(64,0,0);for(var p:List.of(a,b,c))node(h,p);
   Settings first=road(reverse?Style.H4_RAIL:Style.O4_YELLOW),last=road(reverse?Style.O4_YELLOW:Style.H4_RAIL);
   RoadRecord left=data.connect(level,null,a,b,first,null),right=data.connect(level,null,b,c,last,null);
   var x=data.index.roads.get(left.id()).mesh;var y=data.index.roads.get(right.id()).mesh;
   h.assertTrue(x.last().center().distance(y.first().center())<1e-7,"ordinary/highway exact endpoint");
   h.assertTrue(Math.abs(x.last().halfWidth()-y.first().halfWidth())<1e-7,"ordinary/highway shared width");
   var lx=RoadProfile.layout(x,x.last());var ly=RoadProfile.layout(y,y.first());
   h.assertTrue(lx.dividers().equals(ly.dividers()),"ordinary/highway lane dividers match");
   h.assertTrue(RoadWorkChunks.heldCount(level)==0,"connection releases work chunks");
  }
  h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_revision252",timeoutTicks=1200)
 public static void highwayContinuationSurvivesJunctionRebuild(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);BlockPos c=new BlockPos(115600,64,115600),a=c.offset(80,0,0),b=c.offset(-80,0,0),d=c.offset(0,0,80),e=a.offset(80,0,0);
  for(var p:List.of(c,a,b,d,e))node(h,p);
  RoadRecord first=data.connect(level,null,c,a,road(Style.O4_YELLOW),null);
  data.connect(level,null,c,b,road(Style.O4_YELLOW),null);data.connect(level,null,c,d,road(Style.O4_YELLOW),null);
  h.assertTrue(data.streets.containsKey(first.id()),"fixture has a trimmed logical street");
  RoadRecord continuation=data.connect(level,null,a,e,road(Style.H4_RAIL),null);
  var logical=data.streets.get(first.id());h.assertTrue(logical.settings().options().ends().trimmedStart()==0,"logical path stays untrimmed");
  var draft=AutoJunctions.plan(new ArrayList<>(data.streets.values()),new ArrayList<>(data.junctions.values()));
  var rebuilt=draft.roads().stream().filter(r->r.id().equals(first.id())).findFirst().orElseThrow().mesh();var joined=data.index.roads.get(continuation.id()).mesh;
  h.assertTrue(rebuilt.last().center().distance(joined.first().center())<1e-7,"later junction edit retains continuation position");
  h.assertTrue(Math.abs(rebuilt.last().halfWidth()-joined.first().halfWidth())<1e-7,"later junction edit retains continuation width");
  h.assertTrue(RoadProfile.layout(rebuilt,rebuilt.last()).dividers().equals(RoadProfile.layout(joined,joined.first()).dividers()),"later junction edit retains continuation markings");h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_revision252",timeoutTicks=1200)
 public static void shallowGroundDeckHasNoRaisedGuardrail(GameTestHelper h){
  var level=h.getLevel();int origin=114800;
  for(int x=origin-2;x<=origin+50;x++)for(int z=origin-14;z<=origin+14;z++){
   var p=new BlockPos(x,63,z);level.getChunkAt(p);level.setBlock(p,Blocks.STONE.defaultBlockState(),2);
  }
  Node a=new Node(new V(origin+.5,63.98,origin+.5),-90,0),b=new Node(new V(origin+48.5,63.98,origin+.5),-90,0);
  Settings s=road(Style.O4_YELLOW).options(RoadProfile.Options.DEFAULT.extras(false,false,true));
  var r=new RoadRecord(UUID.randomUUID(),new UUID(0,0),BlockPos.containing(a.position().x(),64,a.position().z()),BlockPos.containing(b.position().x(),64,b.position().z()),a,b,s,false,4);
  var built=new RoadIndex.Built(r);var planned=StructurePlanner.plan(level,built,List.of(built),Map.of(),new HashMap<>());
  h.assertTrue(planned.structures().stream().noneMatch(p->p.material()==RoadStructures.Material.CONCRETE&&p.height()>.6&&!p.pier()),"terrain intersecting thin deck is ground, not a bridge");
  h.assertTrue(planned.structures().stream().anyMatch(p->p.material()==RoadStructures.Material.CONCRETE&&p.height()==.2),"ground curb retained");h.succeed();
 }
}
