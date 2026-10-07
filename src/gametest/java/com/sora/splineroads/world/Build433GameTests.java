package com.sora.splineroads.world;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_hotfix433") @PrefixGameTestTemplate(false)
public final class Build433GameTests {
 @GameTest(batch="splineroads_hotfix433",template="empty",templateNamespace="splineroads_hotfix433",timeoutTicks=12000)
 public static void addSaveDelete(GameTestHelper h){Build430GameTests.addLaneActualBuildSaveDelete(h);}
 @GameTest(batch="splineroads_hotfix433",template="empty",templateNamespace="splineroads_hotfix433",timeoutTicks=12000)
 public static void temporaryFurniture(GameTestHelper h){Build430GameTests.temporaryMergeWithHighwayFurniture(h);}
 @GameTest(batch="splineroads_hotfix433",template="empty",templateNamespace="splineroads_hotfix433",timeoutTicks=12000)
 public static void actualLocalTunnelAndPiers(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);
  for(int x=97990;x<=98010;x++)for(int z=97890;z<=98210;z++){
   var p=new BlockPos(x,130,z);level.getChunkAt(p);level.setBlock(p,Blocks.STONE.defaultBlockState(),2);
   if(z>=98030&&z<=98070)level.setBlock(new BlockPos(x,145,z),Blocks.STONE.defaultBlockState(),2);
  }
  var a=Revision32GameTests.marker(h,98000,140,97900,0);var b=Revision32GameTests.marker(h,98000,140,98000,0);
  var c=Revision32GameTests.marker(h,98000,140,98100,0);var e=Revision32GameTests.marker(h,98000,140,98200,0);
  var source=data.connect(level,null,a,b,Revision32GameTests.road(Style.O1_ONE,Structure.AUTO),null);
  var target=data.connect(level,null,c,e,Revision32GameTests.road(Style.O1_ONE,Structure.AUTO),null);
  var from=Build429GameTests.point(data,source,source.mesh().length(),0);var to=Build429GameTests.point(data,target,0,0);
  var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.FLOW,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  var ramp=LaneRamps.generate(data,LaneTopology.records(data),UUID.randomUUID(),source.owner(),new LanePoints.Link(from,to,options,null));
  LaneRamps.build(data,level,null,ramp);var built=data.index.roads.get(ramp.id());
  h.assertTrue(built!=null&&built.cells.size()>0,"actual connector not committed");
  h.assertTrue(!RoadAutoTunnels.regions(built.record.structures()).isEmpty(),"buried part lacks persistent local tunnel");
  h.assertTrue(built.record.structures().stream().anyMatch(p->p.material()==RoadStructures.Material.TUNNEL),"no real lining");
  h.assertTrue(built.record.structures().stream().anyMatch(p->p.pier()&&p.material()==RoadStructures.Material.CONCRETE),"exposed elevated segment has no real support");
  var center=RoadStructures.sample(built.mesh,built.mesh.length()/2).center();var interior=BlockPos.containing(center.x(),center.y()+2,center.z());
  h.assertTrue(built.tunnelAt(interior),"local tunnel interior not owned");
  h.assertTrue(level.getBlockState(interior).is(SplineRoads.TUNNEL_AIR.get()),"actual tunnel interior not protected dry air");
  var saved=RoadData.load(data.save(new CompoundTag()));h.assertTrue(saved.index.roads.get(ramp.id()).hasTunnel(),"local tunnel lost on actual NBT save/load");
  data.remove(level,null,ramp.id());h.assertTrue(!data.index.roads.containsKey(ramp.id()),"local tunnel delete failed");
  System.out.println("BUILD433 LOCAL_TUNNEL_PIERS_SAVE_DELETE_PASS");h.succeed();
 }
}
