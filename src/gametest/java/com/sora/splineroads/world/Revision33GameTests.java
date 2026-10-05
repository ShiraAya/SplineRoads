package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadInfrastructure.*;
import com.sora.splineroads.core.RoadStructures.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_revision33") @PrefixGameTestTemplate(false)
public final class Revision33GameTests {
 static Settings road(Style style,Structure kind){return Revision32GameTests.road(style,kind);}
 static BlockPos marker(GameTestHelper h,int x,int y,int z,double yaw){return Revision32GameTests.marker(h,x,y,z,yaw);}
 @GameTest(template="empty",templateNamespace="splineroads_revision33",timeoutTicks=12000)
 public static void closeUnrelatedRoadKeepsExactRecord(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);var a=marker(h,46000,2,46000,-90);var b=marker(h,46080,2,46000,-90);var c=marker(h,46000,2,46011,-90);var e=marker(h,46080,2,46011,-90);
  var first=d.connect(l,null,a,b,road(Style.O2_YELLOW,Structure.GROUND),null);var other=d.connect(l,null,c,e,road(Style.O2_YELLOW,Structure.GROUND),null);var original=d.index.roads.get(other.id());var saved=original.record.save();
  for(boolean tactile:List.of(false,true,false)){
   var s=first.settings();s=s.options(s.options().sidewalk(new RoadSidewalks.Config(true,RoadSidewalks.Side.LEFT,3,"minecraft:stone_bricks").tactile(tactile)));
   d.connect(l,null,a,b,s,first.id());h.assertTrue(d.index.roads.get(other.id())==original,"nearby unconnected road must not be re-planned");h.assertTrue(d.index.roads.get(other.id()).record.save().equals(saved),"unrelated record stays exact after repeated changes");
  }
  boolean rejected=false;try{d.connect(l,null,a,b,first.settings(),other.id());}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected&&d.index.roads.get(other.id())==original,"stale target ID cannot modify another road");
  d.remove(l,null,first.id());h.assertTrue(d.index.roads.get(other.id())==original,"deleting neighbour does not regenerate this road");d.remove(l,null,other.id());h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_revision33",timeoutTicks=12000)
 public static void diagonalMedianKeepsSinglePiers(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);
  for(int x=46975;x<=47085;x++)for(int z=46975;z<=47085;z++){var p=new BlockPos(x,0,z);l.getChunkAt(p);l.setBlock(p,Blocks.STONE.defaultBlockState(),2);}
  var a=marker(h,47000,13,47000,-45);var b=marker(h,47060,13,47060,-45);var c=marker(h,46995,2,46995,-45);var e=marker(h,47065,2,47065,-45);
  var upper=d.connect(l,null,a,b,road(Style.O4_YELLOW,Structure.AUTO),null);var lower=d.connect(l,null,c,e,road(Style.O8_GREEN,Structure.GROUND),null);var up=d.index.roads.get(upper.id());var low=d.index.roads.get(lower.id());
  var shafts=up.record.structures().stream().filter(p->p.pier()&&p.material()==Material.CONCRETE&&p.height()>2).toList();h.assertTrue(!shafts.isEmpty(),"median has supports");
  for(var shaft:shafts)h.assertTrue(Math.abs(RoadQueries.horizontal(low.mesh,shaft.a()).lateral())<.01,"central shaft must stay inside median rather than become portal");
  d.remove(l,null,upper.id());d.remove(l,null,lower.id());h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_revision33",timeoutTicks=12000)
 public static void logicalTunnelStillExtendsEndpoints(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);var a=marker(h,48000,2,48000,-90);var b=marker(h,48040,2,48000,-90);
  var r=d.connect(l,null,a,b,road(Style.O2_YELLOW,Structure.GROUND),null);
  // This is the logical-street state retained after an earlier junction was removed.
  d.streets.put(r.id(),r);
  var s=road(Style.O2_YELLOW,Structure.TUNNEL);s=s.options(s.options().infrastructure(s.options().infrastructure().depth(6).grade(.10).adjustment(RoadTunnelFit.Adjustment.END)));
  var tunnel=d.connect(l,null,a,b,s,r.id(),false,false,false);
  h.assertTrue(tunnel.start().position().distance(r.start().position())<1e-7,"fixed tunnel entrance stays put");h.assertTrue(tunnel.end().position().x()>r.end().position().x()+20,"logical tunnel exit is automatically extended");h.assertTrue(!tunnel.b().equals(b)&&l.getBlockEntity(tunnel.b()) instanceof NodeEntity,"exit marker moves with tunnel");
  h.assertTrue(d.streets.get(r.id()).end().equals(tunnel.end())&&d.streets.get(r.id()).b().equals(tunnel.b()),"logical path follows relocated tunnel marker");
  var saved=RoadData.load(d.save(new CompoundTag()));h.assertTrue(saved.index.roads.get(r.id()).record.end().equals(tunnel.end()),"adjusted tunnel endpoint survives reload");
  d.remove(l,null,r.id());h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_revision33",timeoutTicks=12000)
 public static void oppositeSideEditKeepsNeighbourTerrainRing(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);
  for(int x=49000;x<=49070;x++)for(int z=48976;z<=49045;z++){var pos=new BlockPos(x,1,z);l.getChunkAt(pos);l.setBlock(pos,Blocks.GRASS_BLOCK.defaultBlockState(),2);}
  var a=marker(h,49004,2,49000,-90);var b=marker(h,49064,2,49000,-90);var c=marker(h,49004,2,49026,-90);var e=marker(h,49064,2,49026,-90);
  var s=road(Style.O2_YELLOW,Structure.GROUND);var first=d.connect(l,null,a,b,s,null);
  var walk=new RoadSidewalks.Config(true,RoadSidewalks.Side.LEFT,5,"minecraft:stone_bricks");
  var other=d.connect(l,null,c,e,s.options(s.options().sidewalk(walk)),null);
  var ring=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
  for(int x=49010;x<49060;x++)for(int z=49010;z<49026;z++){var pos=new BlockPos(x,1,z);if(l.getBlockState(pos).is(Blocks.STONE_BRICKS))ring.put(pos,l.getBlockState(pos));}
  h.assertTrue(ring.size()>20,"neighbour has vanilla sidewalk ring");
  d.connect(l,null,a,b,s.options(s.options().sidewalk(walk.width(15))),first.id());
  ring.forEach((pos,state)->h.assertTrue(l.getBlockState(pos).equals(state),"wide opposite-side edit retains neighbour terrain "+pos));
  d.remove(l,null,first.id());ring.forEach((pos,state)->h.assertTrue(l.getBlockState(pos).equals(state),"removal retains neighbour terrain"));d.remove(l,null,other.id());h.succeed();
 }

}
