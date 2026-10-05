package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import com.sora.splineroads.SplineRoads;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_commands35") @PrefixGameTestTemplate(false)
public final class Command35GameTests {
 static void command(GameTestHelper h,CommandSourceStack source,String command){try{source.getServer().getCommands().getDispatcher().execute(command,source);}catch(CommandSyntaxException e){throw new AssertionError(command,e);}}
 static void drain(GameTestHelper h){for(int i=0;i<200&&RoadCommands.pending(h.getLevel().getServer());i++)RoadCommands.tick(h.getLevel().getServer());h.assertTrue(!RoadCommands.pending(h.getLevel().getServer()),"bounded cleanup completes");}
 @GameTest(template="empty",templateNamespace="splineroads_commands35",timeoutTicks=2000)
 public static void aliasesAndCylinderCleanup(GameTestHelper h)throws Exception{
  var l=h.getLevel();var player=FakePlayerFactory.getMinecraft(l);BlockPos p=new BlockPos(61000,20,61000);player.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);var source=player.createCommandSourceStack().withPermission(4);
  command(h,source,"sr day");h.assertTrue(l.getDayTime()%24000==1000,"day matches vanilla");command(h,source,"sr night");h.assertTrue(l.getDayTime()%24000==18000,"night matches midnight");
  command(h,source,"sr rain");h.assertTrue(l.getLevelData().isRaining(),"rain enabled");command(h,source,"sr sun");h.assertTrue(!l.getLevelData().isRaining(),"rain cleared");
  var itemPos=h.absolutePos(new BlockPos(1,2,1));var item=new ItemEntity(l,itemPos.getX(),itemPos.getY(),itemPos.getZ(),new ItemStack(Items.COBBLESTONE));h.assertTrue(l.addFreshEntity(item),"item fixture added in active test chunk");command(h,source,"sr kd");h.assertTrue(!item.isAlive(),"kd kills item entities");
  var dispatcher=l.getServer().getCommands().getDispatcher();boolean denied=false;
  try{dispatcher.execute("sr day",source.withPermission(0));}catch(CommandSyntaxException e){denied=true;}h.assertTrue(denied,"operator permissions required");
  for(String species:List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry")){
   Block log=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation("minecraft",species+"_log"));
   Block leaf=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation("minecraft",species+"_leaves"));
   Block stripped=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation("minecraft","stripped_"+species+"_wood"));
   l.setBlockAndUpdate(p,log.defaultBlockState());l.setBlockAndUpdate(p.above(),leaf.defaultBlockState());l.setBlockAndUpdate(p.east(),stripped.defaultBlockState());l.setBlockAndUpdate(p.west(),(species.equals("birch")?Blocks.OAK_LOG:Blocks.BIRCH_LOG).defaultBlockState());
   command(h,source,"sr tree 3 2 "+species);drain(h);
   h.assertTrue(l.getBlockState(p).is(log)&&l.getBlockState(p.above()).is(leaf)&&l.getBlockState(p.east()).is(stripped),"excluded species preserved including stripped wood: "+species);h.assertTrue(l.getBlockState(p.west()).isAir(),"other species removed");
  }
  l.setBlockAndUpdate(p,Blocks.OAK_LOG.defaultBlockState());l.setBlockAndUpdate(p.above(),Blocks.OAK_LEAVES.defaultBlockState());l.setBlockAndUpdate(p.above(3),Blocks.BIRCH_LOG.defaultBlockState());l.setBlockAndUpdate(p.offset(3,0,3),Blocks.BIRCH_LOG.defaultBlockState());l.setBlockAndUpdate(p.below(),Blocks.BIRCH_LOG.defaultBlockState());
  command(h,source,"sr tree 3 oak");drain(h);h.assertTrue(l.getBlockState(p).is(Blocks.OAK_LOG),"exception accepts omitted height");
  command(h,source,"sr tree 3");drain(h);h.assertTrue(l.getBlockState(p).isAir()&&l.getBlockState(p.above()).isAir(),"default height removes logs and leaves");
  h.assertTrue(l.getBlockState(p.above(3)).is(Blocks.BIRCH_LOG)&&l.getBlockState(p.offset(3,0,3)).is(Blocks.BIRCH_LOG)&&l.getBlockState(p.below()).is(Blocks.BIRCH_LOG),"height, circular edge and lower bound preserved");
  l.setBlock(p,Blocks.POPPY.defaultBlockState(),2);l.setBlock(p.east(),Blocks.GRASS.defaultBlockState(),2);l.setBlock(p.west(),Blocks.FERN.defaultBlockState(),2);l.setBlock(p.north(),Blocks.SUNFLOWER.defaultBlockState(),2);l.setBlock(p.north().above(),Blocks.SUNFLOWER.defaultBlockState().setValue(DoublePlantBlock.HALF,DoubleBlockHalf.UPPER),2);
  l.setBlock(p.south(),Blocks.WHEAT.defaultBlockState(),2);l.setBlock(p.east(2),Blocks.OAK_SAPLING.defaultBlockState(),2);l.setBlock(p.west(2),Blocks.STONE.defaultBlockState(),2);
  l.setBlock(p.offset(2,1,0),Blocks.CHERRY_LEAVES.defaultBlockState(),18);l.setBlock(p.offset(-2,1,0),Blocks.MANGROVE_PROPAGULE.defaultBlockState(),18);
  command(h,source,"sr plant 3 2");drain(h);
  for(BlockPos q:List.of(p,p.east(),p.west(),p.north(),p.north().above()))h.assertTrue(l.getBlockState(q).isAir(),"grass and flowers removed at "+q);
  h.assertTrue(l.getBlockState(p.south()).is(Blocks.WHEAT)&&l.getBlockState(p.east(2)).is(Blocks.OAK_SAPLING)&&l.getBlockState(p.west(2)).is(Blocks.STONE),"crops saplings and building blocks retained");
  h.assertTrue(l.getBlockState(p.offset(2,1,0)).is(Blocks.CHERRY_LEAVES)&&l.getBlockState(p.offset(-2,1,0)).is(Blocks.MANGROVE_PROPAGULE),"flower tag must not clear leaves or propagules");
  l.setBlock(p,Blocks.DEAD_BUSH.defaultBlockState(),2);command(h,source,"sr plant 3");drain(h);h.assertTrue(l.getBlockState(p).isAir(),"plant default height");
  for(String bad:List.of("sr tree 0","sr tree 3 invalid","sr plant 3 0")){boolean failed=false;try{dispatcher.execute(bad,source);}catch(CommandSyntaxException e){failed=true;}h.assertTrue(failed,"invalid command rejected: "+bad);}
  h.succeed();
 }
}
