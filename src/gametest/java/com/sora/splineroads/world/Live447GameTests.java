package com.sora.splineroads.world;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.net.RoadPlanningJobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.gametest.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import java.util.*;
@GameTestHolder("splineroads_live447") @PrefixGameTestTemplate(false)
public final class Live447GameTests {
 @GameTest(batch="splineroads_live447",template="empty",templateNamespace="splineroads_live447",timeoutTicks=18000)
 public static void temporaryDirectSameEndpoints(GameTestHelper h){scenario(h,280000,LanePoints.Departure.TEMPORARY);}
 @GameTest(batch="splineroads_live447",template="empty",templateNamespace="splineroads_live447",timeoutTicks=18000)
 public static void extraDirectSameEndpoints(GameTestHelper h){scenario(h,282000,LanePoints.Departure.EXTRA);}
 private static Settings sourceSettings(){
  var o=RoadProfile.Options.DEFAULT.lanes(new RoadLanes.Counts(4,4)).cycleFinish(RoadProfile.Options.CycleFinish.ASPHALT);
  o=o.streetscape(o.streetscape().separator(RoadStreetscape.Separator.GREEN)).sidewalk(new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks",true,true));
  return new Settings(Mode.STRAIGHT,Style.O8_GREEN,44,1,.35,90).options(o);
 }
 private static void scenario(GameTestHelper h,int origin,LanePoints.Departure departure){
  var level=h.getLevel();var data=RoadData.get(level);var player=FakePlayerFactory.getMinecraft(level);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
  for(int dx=-35;dx<=35;dx++)for(int dz=-5;dz<=310;dz++){
   var p=new BlockPos(origin+dx,199,origin+dz);level.getChunkAt(p);level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);
  }
  var source=data.connect(level,player,marker(h,player,origin,200,origin+300,-180),marker(h,player,origin,200,origin,-180),sourceSettings(),null);
  var targetSettings=new Settings(Mode.STRAIGHT,Style.O6_GREEN,28,1,.35,90);
  var target=data.connect(level,player,marker(h,player,origin,208,origin-4,0),marker(h,player,origin,208,origin+305,0),targetSettings,null);
  var a=Build429GameTests.point(data,source,281.0640078009419,3);var b=Build429GameTests.point(data,target,86.8331478396766,5);
  var options=new LanePoints.Options(LanePoints.Path.DIRECT,departure,LanePoints.Arrival.EXTRA,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.FLEXIBLE,false,false);
  var tool=new ItemStack(SplineRoads.RAMP_CONNECTOR.get());player.setItemInHand(InteractionHand.MAIN_HAND,tool);
  var command=new CompoundTag();command.put("From",LanePointCodec.ref(a));command.put("To",LanePointCodec.ref(b));command.put("Options",LanePointCodec.options(options));command.putLong("Request",447);
  tool.getOrCreateTag().put("LaneFrom",command.getCompound("From").copy());tool.getOrCreateTag().put("LaneTo",command.getCompound("To").copy());tool.getOrCreateTag().putString("LaneDimension",level.dimension().location().toString());
  // A network-like thread must cancel without waiting for the server thread. The
  // real job registry/token is inspected only by this integration test.
  if(departure==LanePoints.Departure.TEMPORARY)try{
   RoadPlanningJobs.begin(player,tool,command);
   var registry=RoadPlanningJobs.class.getDeclaredField("ACTIVE");registry.setAccessible(true);
   Object job=((Map<?,?>)registry.get(null)).get(player.getUUID());h.assertTrue(job!=null,"no active job");
   var field=job.getClass().getDeclaredField("cancelled");field.setAccessible(true);var token=(java.util.concurrent.atomic.AtomicBoolean)field.get(job);
   var started=new java.util.concurrent.CountDownLatch(1);var canceller=new Thread(()->{try{started.await();RoadPlanningJobs.cancel(player.getUUID(),447);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
   canceller.start();long cancelStart=System.nanoTime();boolean aborted=false;
   try(var budget=RoadPlanningBudget.cancellable("test active server validation",token::get)){
    started.countDown();while(System.nanoTime()-cancelStart<2_000_000_000L)RoadPlanningBudget.check();
   }catch(RoadPlanningBudget.Aborted expected){aborted=true;}
   h.assertTrue(aborted&&RoadPlanningJobs.activeCount()==0,"cancellation queued behind validation");
   h.assertTrue(!tool.getOrCreateTag().contains("LanePreview"),"canceled computation published a preview");
   System.out.println("LIVE447 CANCEL PASS real job/token canceled from another thread without server queue");
  }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
  var work=LaneRamps.preparePreview(player,tool,command);long revision=data.index.revision(),start=System.nanoTime();CompoundTag reply;
  try(var budget=RoadPlanningBudget.open("LIVE447 real preview",90)){reply=work.resolve(route->LaneRamps.finishPreview(player,tool,command,work,route));}
  h.assertTrue(data.index.revision()==revision,"preview mutated world roads");
  System.out.printf("LIVE447 PREVIEW PASS %s %.3fs%n",departure,(System.nanoTime()-start)/1e9);
  var preview=RoadRecord.load(reply.getCompound("Road"));command.putUUID("Token",reply.getUUID("Token"));start=System.nanoTime();LaneRamps.build(player,tool,command);
  System.out.printf("LIVE448 CACHED_RAMP_BUILD %s %.3fs%n",departure,(System.nanoTime()-start)/1e9);
  var built=data.index.roads.get(preview.id()).record;LaneRampGrade.validate(built.mesh(),.2);
  if(departure==LanePoints.Departure.TEMPORARY)Live448GameTests.clearWidth(h,data,built);
  h.assertTrue(LaneTopology.metadata(built).link().options().equals(options),"preview/build changed requested mode");
  h.assertTrue(Math.abs(LaneTopology.metadata(built).link().targetOffset())<=88,"flexible landing exceeded declared range");
  var reloaded=RoadData.load(data.save(new CompoundTag()));h.assertTrue(reloaded.index.roads.get(built.id()).record.save().equals(built.save()),"real NBT changed connector");
  data.remove(level,player,built.id());h.assertTrue(!data.index.roads.containsKey(built.id()),"connector remained after delete");
  System.out.println("LIVE447 REAL_WORLD PASS "+departure+": saved-relative endpoints, grass terrain, actual preview/build, NBT and deletion");h.succeed();
 }
 private static BlockPos marker(GameTestHelper h,net.minecraft.server.level.ServerPlayer player,int x,int y,int z,double yaw){var p=Revision32GameTests.marker(h,x,y,z,yaw);var node=(NodeEntity)h.getLevel().getBlockEntity(p);node.owner=player.getUUID();node.apply(new Node(new V(x+.5,y,z+.5),yaw,0));return p;}
}
