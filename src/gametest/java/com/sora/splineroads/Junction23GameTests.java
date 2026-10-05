package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
@GameTestHolder("splineroads_junction23") @PrefixGameTestTemplate(false)
public final class Junction23GameTests {
 static BlockPos node(GameTestHelper h,int x,int z){BlockPos p=new BlockPos(x,90,z);h.getLevel().setBlock(p,SplineRoads.NODE.get().defaultBlockState(),3);((NodeEntity)h.getLevel().getBlockEntity(p)).owner=new UUID(0,0);return p;}
 static Settings settings(){return new Settings(Mode.STRAIGHT,Style.O2_YELLOW,9,1,.35,90).structure(Structure.GROUND);}
 @GameTest(template="empty",templateNamespace="splineroads_junction23",timeoutTicks=200)
 public static void manualModesKeepHeadingsInAutomaticJunctionPreview(GameTestHelper h) {
  CompoundTag command=new CompoundTag();command.putLong("A",new BlockPos(0,90,0).asLong());command.putLong("B",new BlockPos(100,90,80).asLong());
  Node a=new Node(new V(.5,90,.5),-50,0),b=new Node(new V(100.5,90,80.5),-80,0);command.put("StartNode",RoadRecord.writeNode(a));command.put("EndNode",RoadRecord.writeNode(b));command.putBoolean("CenterA",true);command.putBoolean("CenterB",true);
  var lengths=new HashSet<Long>();for(Mode mode:new Mode[]{Mode.STRAIGHT,Mode.CURVE,Mode.ARC}) {
    Settings s=new Settings(mode,Style.O2_YELLOW,9,1,.35,70);command.put("Settings",RoadRecord.writeSettings(s));var proposed=AutoJunctions.proposed(command);
    h.assertTrue(proposed.settings().mode()==mode,"manual mode reaches actual planner: "+mode);
    var expected=RoadGeometry.build(a,b,s);h.assertTrue(proposed.mesh().equals(expected),"junction branch uses same manual shape as ordinary preview: "+mode);lengths.add(Math.round(proposed.mesh().length()*100));
  }
  h.assertTrue(lengths.size()==3,"line, curve and arc create three distinct paths");h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_junction23",timeoutTicks=1200)
 public static void realSidewalkBlocksEditReloadAndRestore(GameTestHelper h) {
  var l=h.getLevel();var d=RoadData.get(l);var a=node(h,-56000,-56000);var b=node(h,-56000,-55940);
  var s=settings().options(RoadProfile.Options.DEFAULT.sidewalk(RoadSidewalks.Config.DEFAULT.enabled(true)));
  var r=d.connect(l,null,a,b,s,null);var planned=SmartSidewalks.plan(List.of(d.index.roads.get(r.id())));
  h.assertTrue(!planned.isEmpty(),"sidewalk footprint exists");
  for(var e:planned.entrySet())h.assertTrue(l.getBlockState(BlockPos.of(e.getKey())).is(Blocks.STONE_BRICKS),"real stone brick block, not SR collision mesh");
  var original=d.save(new CompoundTag());var loaded=RoadData.load(original);h.assertTrue(loaded.save(new CompoundTag()).getList("SidewalkPalette",Tag.TAG_COMPOUND).stream().mapToInt(t->((CompoundTag)t).getLongArray("Positions").length).sum()==planned.size(),"placed block ownership survives reload");
  var next=s.options(s.options().sidewalk(s.options().sidewalk().side(RoadSidewalks.Side.LEFT).width(1).material("minecraft:bricks")));
  d.connect(l,null,a,b,next,r.id());var narrow=SmartSidewalks.plan(List.of(d.index.roads.get(r.id())));
  h.assertTrue(narrow.size()<planned.size(),"one side and width change shrink footprint");
  for(var e:narrow.entrySet())h.assertTrue(l.getBlockState(BlockPos.of(e.getKey())).is(Blocks.BRICKS),"actual block material updates");
  for(long key:planned.keySet())if(!narrow.containsKey(key))h.assertTrue(!l.getBlockState(BlockPos.of(key)).is(Blocks.STONE_BRICKS),"removed sidewalk restores terrain");
  int before=d.index.roads.size();boolean refused=false;try{d.connect(l,null,a,b,next.options(next.options().sidewalk(next.options().sidewalk().material("unknown23:missing_block"))),r.id());}catch(IllegalArgumentException e){refused=true;}
  h.assertTrue(refused&&d.index.roads.size()==before,"unknown mod block rejected atomically");
  for(long key:narrow.keySet())h.assertTrue(l.getBlockState(BlockPos.of(key)).is(Blocks.BRICKS),"failed change leaves sidewalk untouched");
  // A player's replacement remains after removing the automatically generated sidewalk.
  var own=BlockPos.of(narrow.keySet().iterator().next());l.setBlock(own,Blocks.GOLD_BLOCK.defaultBlockState(),3);
  d.remove(l,null,r.id());h.assertTrue(l.getBlockState(own).is(Blocks.GOLD_BLOCK),"player replacement preserved");
  for(long key:narrow.keySet())if(key!=own.asLong())h.assertTrue(!l.getBlockState(BlockPos.of(key)).is(Blocks.BRICKS),"delete restores former terrain");
  h.assertTrue(RoadWorkChunks.heldCount(l)==0,"all sidewalk chunks released");h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_junction23",timeoutTicks=1200)
 public static void roundaboutRemotePortDoesNotBlockContinuation(GameTestHelper h) {
  var l=h.getLevel();var d=RoadData.get(l);var center=node(h,-57000,-57000);var request=new CompoundTag();request.put("Node",RoadRecord.writeNode(((NodeEntity)l.getBlockEntity(center)).constructionNode()));request.putDouble("Radius",18);request.putInt("Lanes",2);var spec=RoundaboutTool.specification(request);
  for(var port:RoundaboutTool.ports(spec))l.setBlock(BlockPos.containing(port.position().x(),90,port.position().z()),Blocks.AIR.defaultBlockState(),3);
  AutoJunctions.createRing(l,null,center,spec);var port=RoundaboutTool.ports(spec).get(0);var at=BlockPos.containing(port.position().x(),90,port.position().z());var direction=port.position().sub(spec.center()).horizontalUnit();V far=port.position().add(direction.mul(100));var end=node(h,(int)Math.floor(far.x()),(int)Math.floor(far.z()));var first=d.connect(l,null,at,end,settings(),null);
  V beyond=far.add(direction.mul(80));var next=node(h,(int)Math.floor(beyond.x()),(int)Math.floor(beyond.z()));var extension=d.connect(l,null,end,next,settings(),null);
  h.assertTrue(d.index.roads.containsKey(extension.id()),"continuation succeeds despite unused ring ports");
  for(var p:RoundaboutTool.ports(spec))h.assertTrue(l.getBlockEntity(BlockPos.containing(p.position().x(),90,p.position().z())) instanceof NodeEntity,"unused ring markers preserved");
  var checkStart=node(h,-58000,-58000);var unrelated=node(h,-58000,-57950);var target=node(h,-58000,-57900);boolean refused=false;int count=d.index.roads.size();try{d.connect(l,null,checkStart,target,settings(),null);}catch(IllegalArgumentException e){refused=true;}
  h.assertTrue(refused&&d.index.roads.size()==count,"a genuinely intervening foreign marker still blocks construction");
  d.remove(l,null,extension.id());d.remove(l,null,first.id());AutoJunctions.removeCenter(l,null,AutoJunctions.center(d,center).getUUID("Id"));h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_junction23",timeoutTicks=1200)
 public static void bicycleSeamAndSidewalkSurviveJunctionBuild(GameTestHelper h) {
  var l=h.getLevel();var d=RoadData.get(l);var c=node(h,56000,56000);var a=node(h,56120,56000);var b=node(h,56000,56120);
  var options=RoadProfile.Options.DEFAULT.extras(true,true,true).sidewalk(RoadSidewalks.Config.DEFAULT.enabled(true));var s=new Settings(Mode.AUTO,Style.O4_YELLOW,RoadProfile.width(Style.O4_YELLOW,options,4),1,.35,90).options(options).structure(Structure.GROUND);
  var first=d.connect(l,null,c,a,s,null);var second=d.connect(l,null,c,b,s,null,false,true);
  var approach=d.index.roads.values().stream().filter(r->r.record.junction()!=null&&r.record.junction().get().arm()>=0).filter(r->r.record.a().equals(c)).findFirst().orElseThrow();
  h.assertTrue(approach.record.junction().geometryVersion()==26,"current geometry version recorded while legacy 23 remains readable");
  for(var street:List.of(first,second)) {
   var live=d.index.roads.get(street.id());var near=d.index.roads.values().stream().filter(r->r.record.junction()!=null&&r.record.junction().get().arm()>=0).filter(r->r.mesh.first().center().distance(live.mesh.first().center())<.001||r.mesh.first().center().distance(live.mesh.last().center())<.001).findFirst().orElseThrow();
   double half=near.mesh.first().halfWidth();double expected=near.mesh.first().center().distance(live.mesh.first().center())<.001?live.mesh.first().halfWidth():live.mesh.last().halfWidth();h.assertTrue(Math.abs(half-expected)<1e-6,"physical seam width matches including padding");
   h.assertTrue(near.record.structures().stream().anyMatch(p->p.material()==RoadStructures.Material.STEEL&&Math.min(p.a().distance(near.mesh.first().center()),p.b().distance(near.mesh.first().center()))<12),"cycle rails reach approach mouth");
  }
  h.assertTrue(!SmartSidewalks.plan(d.index.roads.values()).isEmpty(),"real sidewalk still exists around trimmed ordinary approaches");
  d.remove(l,null,second.id());d.remove(l,null,first.id());h.succeed();
 }
}
