package com.sora.splineroads.world;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;import net.minecraft.gametest.framework.*;import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;import net.minecraft.world.item.ItemStack;import net.minecraft.world.InteractionHand;
import net.minecraftforge.gametest.*;import net.minecraftforge.common.util.FakePlayerFactory;import java.util.*;
@GameTestHolder("splineroads_live440") @PrefixGameTestTemplate(false)
public final class Live440GameTests {
 @GameTest(batch="splineroads_live440",template="empty",templateNamespace="splineroads_live440",timeoutTicks=12000)
 public static void requestComputePublishBuild(GameTestHelper h){scenario(h,false,200000);}
 @GameTest(batch="splineroads_live440",template="empty",templateNamespace="splineroads_live440",timeoutTicks=12000)
 public static void undergroundAllowedWithoutPlanter(GameTestHelper h){scenario(h,true,202000);}
 private static void scenario(GameTestHelper h,boolean underground,int cx){
  var level=h.getLevel();var data=RoadData.get(level);int cz=cx;
  var player=FakePlayerFactory.getMinecraft(level);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
  for(int x=cx-15;x<=cx+110;x++)for(int z=cz;z<=cz+362;z++){
   var pos=new BlockPos(x,198,z);level.getChunkAt(pos);level.setBlock(pos,Blocks.GRASS_BLOCK.defaultBlockState(),2);
   if(underground&&z>=cz+110&&z<=cz+170)for(int y=199;y<=205;y++)level.setBlock(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),2);
  }
  var settings=RoadLanes.configure(Revision32GameTests.road(Style.O1_ONE,Structure.AUTO),RoadProfile.Type.ORDINARY,new RoadLanes.Counts(1,0),4);
  settings=settings.options(settings.options().route(settings.options().routing().fit(false)));
  var source=data.connect(level,player,Revision32GameTests.marker(h,cx,200,cz,0),Revision32GameTests.marker(h,cx,200,cz+80,0),settings,null);
  int by=underground?200:208;
  var target=data.connect(level,player,Revision32GameTests.marker(h,cx+90,by,cz+200,0),Revision32GameTests.marker(h,cx+90,by,cz+360,0),settings,null);
  var from=Build429GameTests.point(data,source,40,0);var to=Build429GameTests.point(data,target,60,0);
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.FLEXIBLE);
  var tool=new ItemStack(SplineRoads.RAMP_CONNECTOR.get());player.setItemInHand(InteractionHand.MAIN_HAND,tool);
  var command=new CompoundTag();command.put("From",LanePointCodec.ref(from));command.put("To",LanePointCodec.ref(to));command.put("Options",LanePointCodec.options(options));command.putLong("Request",440);
  tool.getOrCreateTag().put("LaneFrom",command.getCompound("From").copy());tool.getOrCreateTag().put("LaneTo",command.getCompound("To").copy());tool.getOrCreateTag().putString("LaneDimension",level.dimension().location().toString());
  var work=LaneRamps.preparePreview(player,tool,command);var route=work.compute();var generated=route.road();
  // A flexible worker may select another B station. Exercise publication against
  // the unchanged raw request, exactly where 0.40.26 lost the computed link.
  if(!underground){var link=LaneTopology.metadata(generated).link().targetOffset(16);generated=LaneRamps.generate(null,LaneTopology.records(data),generated.id(),generated.owner(),link);route=new LaneRamps.PreviewRoute(generated,LanePoints.Path.AUTO);}
  var before=generated;long revision=data.index.revision();
  var reply=LaneRamps.finishPreview(player,tool,command,work,route);
  h.assertTrue(data.index.revision()==revision&&!data.index.roads.containsKey(before.id()),"preview wrote actual roads");
  var checked=RoadRecord.load(reply.getCompound("Road"));var resolved=LaneTopology.metadata(checked).link();
  h.assertTrue(resolved.rectangularClosure()&&resolved.protectedMerge(),"publication reverted resolved flags");
  h.assertTrue(resolved.targetOffset()==LaneTopology.metadata(before).link().targetOffset(),"publication reverted B offset");
  command.putUUID("Token",reply.getUUID("Token"));LaneRamps.build(player,tool,command);
  var built=data.index.roads.get(before.id()).record;h.assertTrue(LaneRamps.monotone(built.mesh()),"unnecessary reversal");
  if(underground){
   h.assertTrue(built.mesh().max().y()<201,"underground route was raised to terrain");
   h.assertTrue(!RoadAutoTunnels.regions(built.structures()).isEmpty(),"buried connector lost automatic tunnel lining");
   for(var host:List.of(source,target))h.assertTrue(data.index.roads.get(host.id()).record.structures().stream().noneMatch(p->p.material()==RoadStructures.Material.GREEN||p.material()==RoadStructures.Material.SOIL),"underground ramp reservation generated greenery");
  }else h.assertTrue(resolved.targetOffset()==16,"flexible fixture did not retain changed B");
  if(!underground){var edit=new CompoundTag();edit.putUUID("Id",built.id());edit.putInt("Signature",built.header().hashCode());edit.put("Settings",RoadRecord.writeSettings(built.settings().structure(Structure.TUNNEL)));LaneRamps.editRoad(level,player,edit);built=data.index.roads.get(before.id()).record;h.assertTrue(built.settings().structure()==Structure.TUNNEL,"actual tunnel edit rejected");}
  var reload=RoadData.load(data.save(new CompoundTag()));h.assertTrue(reload.index.roads.get(before.id()).record.save().equals(built.save()),"NBT changed published road");
  var cells=new HashSet<>(data.index.roads.get(before.id()).cells.keySet());data.remove(level,player,before.id());
  for(long p:cells)if(!data.index.occupied(p))h.assertTrue(!RoadBlocks.isCollider(level.getBlockState(BlockPos.of(p))),"orphan collider after delete");
  System.out.println("LIVE440 REAL_WORLD PASS underground="+underground+": actual request/compute/publish/build, rectangle, flexible B, monotone, NBT, deletion");h.succeed();
 }
}
