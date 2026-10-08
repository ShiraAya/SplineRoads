package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Actual ServerLevel preview, transaction, NBT and deletion for the user's 32 cases. */
@GameTestHolder("splineroads_matrix434") @PrefixGameTestTemplate(false)
public final class RampMatrix434GameTests {
 @GameTest(batch="splineroads_matrix434",template="empty",templateNamespace="splineroads_matrix434",timeoutTicks=24000)
 public static void cross501EightMetres(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);int cx=120000,cz=120000;
  boolean fixedEnd=System.getProperty("sr.matrix.end","outgoing").equals("east");
  int extent=fixedEnd?330:275;
  for(int x=cx-extent;x<=cx+extent;x++)for(int z=cz-extent;z<=cz+extent;z++){
   var p=new BlockPos(x,198,z);level.getChunkAt(p);level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);
  }
  var a=Revision32GameTests.marker(h,cx,200,cz-250,0);var b=Revision32GameTests.marker(h,cx,200,cz+250,0);
  var c=Revision32GameTests.marker(h,cx-250,208,cz,-90);var e=Revision32GameTests.marker(h,cx+250,208,cz,-90);
  var s=RoadLanes.configure(Revision32GameTests.road(Style.O6_RAIL,Structure.AUTO),RoadProfile.Type.ORDINARY,new RoadLanes.Counts(3,3),4);
  s=s.options(s.options().route(s.options().routing().fit(false)));
  var ground=data.connect(level,null,a,b,s,null);var upper=data.connect(level,null,c,e,s,null);
  // Free road end caps add half a block at EACH end. The user's 250m
  // measurement is to marker centres, not to the outside of those caps.
  h.assertTrue(Math.abs(ground.end().position().sub(ground.start().position()).horizontalLength()-500)<1e-6
      &&Math.abs(upper.end().position().sub(upper.start().position()).horizontalLength()-500)<1e-6,"marker centres must span exactly 500 metres");
  System.out.println("MATRIX434 FIXTURE node_span=500 fixed_east="+fixedEnd+" mesh_lengths="+ground.mesh().length()+","+upper.mesh().length());
  h.assertTrue(Math.abs(upper.mesh().first().center().y()-ground.mesh().first().center().y()-8)<1e-6,"8 metre level difference changed");
  int selectedRow=Integer.parseInt(System.getProperty("sr.matrix.row","-1"));
  h.assertTrue(selectedRow>=-1&&selectedRow<4,"invalid matrix row");int expected=selectedRow<0?32:8;
  var failures=new ArrayList<String>();int passed=0;
  int[] slots={2,2,1,0,3,4,5,5};
  for(int row=0;row<4;row++)for(int col=1;col<=8;col++){
   if(selectedRow>=0&&row!=selectedRow)continue;
   String label=""+(char)('A'+row)+col;String stage="plan";long started=System.nanoTime();UUID id=UUID.randomUUID();
   try{
    ground=data.index.roads.get(ground.id()).record;upper=data.index.roads.get(upper.id()).record;
    int sourceSlot=row<=1?5:row==2?4:3,targetSlot=slots[col-1];
    var targetLane=LanePoints.lane(upper.mesh(),250,targetSlot);
    double at=RoadQueries.horizontal(upper.mesh(),fixedEnd||targetLane.sign()>0?upper.end().position():upper.start().position()).sample().distance();
    double sourceAt=RoadQueries.horizontal(ground.mesh(),ground.start().position()).sample().distance();
    var from=Build429GameTests.point(data,ground,sourceAt,sourceSlot);var to=Build429GameTests.point(data,upper,at,targetSlot);
    var options=new LanePoints.Options(LanePoints.Path.AUTO,row==0?LanePoints.Departure.EXTRA:LanePoints.Departure.TEMPORARY,
        col==1||col==8?LanePoints.Arrival.EXTRA:LanePoints.Arrival.MERGE,24,32,LanePoints.Elevation.AUTO,fixedEnd?LanePoints.Landing.FLEXIBLE:LanePoints.Landing.EXACT);
    var r=LaneRamps.generate(data,LaneTopology.records(data),id,ground.owner(),new LanePoints.Link(from,to,options,null));
    System.out.println("MATRIX434 "+label+" PLAN_PASS");stage="preview";long revision=data.index.revision();
    var endpoints=new HashSet<BlockPos>(List.of(a,b,c,e,r.a(),r.b()));
    var preview=data.previewAssembly(level,null,List.of(new RoadIndex.Built(r)),Set.of(),endpoints,List.of());
    h.assertTrue(data.index.revision()==revision&&!data.index.roads.containsKey(id),label+" preview wrote to world/index");
    r=preview.stream().filter(v->v.record.id().equals(id)).findFirst().orElseThrow().record;
    System.out.println("MATRIX434 "+label+" PREVIEW_PASS");stage="build";LaneRamps.build(data,level,null,r);
    var built=data.index.roads.get(id);h.assertTrue(built!=null&&!built.cells.isEmpty(),label+" missing actual blocks");
    LaneRamps.validate(built.mesh,LaneTopology.records(data),id,LaneTopology.metadata(built.record).link());
    stage="save";var saved=RoadData.load(data.save(new CompoundTag()));h.assertTrue(saved.index.roads.containsKey(id),label+" NBT lost ramp");
    stage="delete";data.remove(level,null,id);h.assertTrue(!data.index.roads.containsKey(id),label+" delete failed");
    for(var host:List.of(ground.id(),upper.id()))h.assertTrue(LaneTopology.metadata(data.index.roads.get(host).record).cuts().stream().noneMatch(v->v.connection().equals(id)),label+" closure remained after deletion");
    passed++;System.out.println(String.format(Locale.ROOT,"MATRIX434 %s PASS ms=%.3f",label,(System.nanoTime()-started)/1e6));
   }catch(Exception|AssertionError ex){
    failures.add(label+" "+stage+": "+ex.getMessage());System.out.println("MATRIX434 "+label+" FAIL "+stage+" "+ex.getMessage());ex.printStackTrace();
    if(data.index.roads.containsKey(id))data.remove(level,null,id);
   }
  }
  System.out.println("MATRIX434 SUMMARY "+passed+"/"+expected+" row="+selectedRow+" "+failures);
  h.assertTrue(passed==expected&&failures.isEmpty(),String.join("; ",failures));h.succeed();
 }
}
