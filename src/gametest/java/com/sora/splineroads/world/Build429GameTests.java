package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

/** Real server transaction timing, not a client/GPU or user-world fixture. */
@GameTestHolder("splineroads_hotfix429") @PrefixGameTestTemplate(false)
public final class Build429GameTests {
  @GameTest(template="empty",templateNamespace="splineroads_revision32",timeoutTicks=12000)
  public static void ordinaryRoadConstruction(GameTestHelper h) {
    System.setProperty("sr.profile","true");
    var level=h.getLevel();var data=RoadData.get(level);
    for(int x=80000;x<=80320;x++)for(int z=79980;z<=80020;z++){
      var pos=new BlockPos(x,1,z);level.getChunkAt(pos);level.setBlock(pos,Blocks.GRASS_BLOCK.defaultBlockState(),2);
    }
    var a=Revision32GameTests.marker(h,80004,2,80000,-90);
    var b=Revision32GameTests.marker(h,80104,2,80000,-90);
    var c=Revision32GameTests.marker(h,80304,2,80000,-90);
    var settings=Revision32GameTests.road(Style.O6_GREEN,Structure.AUTO);
    long start=System.nanoTime();
    var first=data.connect(level,null,a,b,settings,null);
    System.out.println("BUILD429 FIRST_REAL_WORLD_MS="+(System.nanoTime()-start)/1e6);
    h.assertTrue(data.index.roads.containsKey(first.id()),"first road committed");
    start=System.nanoTime();
    var second=data.connect(level,null,b,c,settings,null);
    System.out.println("BUILD429 CONTINUATION_REAL_WORLD_MS="+(System.nanoTime()-start)/1e6);
    h.assertTrue(data.index.roads.containsKey(second.id()),"continuation committed");
    h.assertTrue(data.index.roads.get(second.id()).cells.size()>0,"real collision cells built");
    h.succeed();
  }
}
