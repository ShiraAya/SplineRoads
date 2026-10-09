package com.sora.splineroads.world;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.gametest.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import java.util.*;
@GameTestHolder("splineroads_live446") @PrefixGameTestTemplate(false)
public final class Live446GameTests {
 @GameTest(batch="splineroads_live446",template="empty",templateNamespace="splineroads_live445",timeoutTicks=18000)
 public static void localOpenAndClosedTunnels(GameTestHelper h){scenario(h,260000,true,false);}
 @GameTest(batch="splineroads_live446",template="empty",templateNamespace="splineroads_live445",timeoutTicks=18000)
 public static void defaultEarthAvoidance(GameTestHelper h){scenario(h,262000,false,false);}
 @GameTest(batch="splineroads_live446",template="empty",templateNamespace="splineroads_live445",timeoutTicks=18000)
 public static void undergroundEndpointException(GameTestHelper h){scenario(h,264000,false,true);}
 private static void scenario(GameTestHelper h,int origin,boolean allow,boolean undergroundEnd){
  var level=h.getLevel();var data=RoadData.get(level);var player=FakePlayerFactory.getMinecraft(level);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
  // Original ground: bridge -> ground -> shallow open cut -> covered -> ground.
  for(int dx=-7;dx<=7;dx++)for(int dz=0;dz<=300;dz++){
   int top=undergroundEnd?(dz<100?206:199):(dz<80?198:dz<100?200:dz<140?203:dz<180?206:199);
   for(int y=195;y<top;y++){var p=new BlockPos(origin+dx,y,origin+dz);level.getChunkAt(p);level.setBlock(p,Blocks.STONE.defaultBlockState(),2);}
  }
  var settings=RoadLanes.configure(Revision32GameTests.road(Style.O1_ONE,Structure.AUTO),RoadProfile.Type.ORDINARY,new RoadLanes.Counts(1,0),4);
  settings=settings.options(settings.options().route(settings.options().routing().fit(false)).infrastructure(settings.options().infrastructure().gantry(RoadInfrastructure.Gantry.OFF)));
  var sourceSettings=undergroundEnd?settings.structure(Structure.TUNNEL).options(settings.options().infrastructure(settings.options().infrastructure().headroom(4))):settings;
  var source=data.connect(level,player,marker(h,player,origin,200,origin),marker(h,player,origin,200,origin+60),sourceSettings,null);
  var target=data.connect(level,player,marker(h,player,origin,undergroundEnd?208:200,origin+220),marker(h,player,origin,undergroundEnd?208:200,origin+300),settings,null);
  var a=Build429GameTests.point(data,source,60,0);var b=Build429GameTests.point(data,target,0,0);
  var options=new LanePoints.Options(LanePoints.Path.DIRECT,LanePoints.Departure.BRANCH,LanePoints.Arrival.FLOW,24,32,undergroundEnd?LanePoints.Elevation.OVER:LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT,false,allow);
  var tool=new ItemStack(SplineRoads.RAMP_CONNECTOR.get());player.setItemInHand(InteractionHand.MAIN_HAND,tool);
  var command=new CompoundTag();command.put("From",LanePointCodec.ref(a));command.put("To",LanePointCodec.ref(b));command.put("Options",LanePointCodec.options(options));command.putLong("Request",446);
  tool.getOrCreateTag().put("LaneFrom",command.getCompound("From").copy());tool.getOrCreateTag().put("LaneTo",command.getCompound("To").copy());tool.getOrCreateTag().putString("LaneDimension",level.dimension().location().toString());
  var work=LaneRamps.preparePreview(player,tool,command);long revision=data.index.revision();
  var reply=work.resolve(route->LaneRamps.finishPreview(player,tool,command,work,route));
  h.assertTrue(data.index.revision()==revision,"preview changed world roads");
  var preview=RoadRecord.load(reply.getCompound("Road"));command.putUUID("Token",reply.getUUID("Token"));LaneRamps.build(player,tool,command);
  var built=data.index.roads.get(preview.id()).record;var mesh=built.mesh();var parts=built.structures();
  h.assertTrue(LaneTopology.metadata(built).link().options().equals(options),"tunnel switch changed crossing mode");
  if(allow){
   h.assertTrue(mesh.max().y()<200.01,"allowed flat underground route was raised");
   h.assertTrue(!RoadAutoTunnels.openRegions(parts).isEmpty(),"shallow segment lacks open-cut walls");
   h.assertTrue(!RoadAutoTunnels.regions(parts).isEmpty(),"deep segment lacks closed roof");
   h.assertTrue(parts.stream().noneMatch(p->p.material()==Material.TUNNEL&&p.a().z()<origin+99),"ground/raised segment misclassified as tunnel");
   h.assertTrue(parts.stream().noneMatch(p->p.material()==Material.TUNNEL&&p.height()<.1&&p.a().z()<origin+140),"open cut got a lid");
   h.assertTrue(parts.stream().anyMatch(p->p.material()==Material.TUNNEL&&p.height()<.1&&Math.abs(p.a().y()-204)<1e-6),"roof does not preserve four-block travel clearance");
   // Verify actual world excavation up to the outside surface in the shallow cut.
   var at=BlockPos.containing(origin,202,origin+120);h.assertTrue(level.getBlockState(at).getCollisionShape(level,at).isEmpty(),"open-cut interior terrain not excavated");
  }else if(undergroundEnd){
   h.assertTrue(!RoadAutoTunnels.regions(parts).isEmpty(),"off switch blocked the underground endpoint exception");
   h.assertTrue(parts.stream().noneMatch(p->p.material()==Material.TUNNEL&&p.a().z()>origin+130),"upper road fabricated a tunnel");
  }else{
   LaneRampTerrain.validate(mesh,options,data.terrainGround());
   h.assertTrue(RoadAutoTunnels.regions(parts).isEmpty()&&RoadAutoTunnels.openRegions(parts).isEmpty(),"toggle off still generated avoidable underground segment");
   h.assertTrue(mesh.max().y()>=206-1e-5,"default route did not avoid the hill");
  }
  var reloaded=RoadData.load(data.save(new CompoundTag()));h.assertTrue(reloaded.index.roads.get(built.id()).record.save().equals(built.save()),"local sections changed through real Mojang NBT");
  var cells=new HashSet<>(data.index.roads.get(built.id()).cells.keySet());data.remove(level,player,built.id());
  for(long key:cells)if(!data.index.occupied(key))h.assertTrue(!RoadBlocks.isCollider(level.getBlockState(BlockPos.of(key))),"orphan wall after deletion");
  System.out.println("LIVE446 REAL_WORLD PASS allow="+allow+" undergroundEnd="+undergroundEnd+": original terrain, publication/build, local sections, NBT and delete");h.succeed();
 }
 private static BlockPos marker(GameTestHelper h,net.minecraft.server.level.ServerPlayer player,int x,int y,int z){var p=Revision32GameTests.marker(h,x,y,z,0);((NodeEntity)h.getLevel().getBlockEntity(p)).owner=player.getUUID();return p;}
}
