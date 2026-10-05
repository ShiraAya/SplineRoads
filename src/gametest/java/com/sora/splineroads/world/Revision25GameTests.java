package com.sora.splineroads.world;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;
@GameTestHolder("splineroads_revision25") @PrefixGameTestTemplate(false)
public final class Revision25GameTests {
 @GameTest(template="empty",templateNamespace="splineroads_revision25",timeoutTicks=18000)
 public static void minimumSixWayActuallyBuilds(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);long[] points=new long[6];double[] angles={0,180,60,240,120,300};
  for(int i=0;i<6;i++){double t=Math.toRadians(angles[i]);BlockPos p=new BlockPos(92000+(int)Math.round(Math.cos(t)*900),90,92000+(int)Math.round(Math.sin(t)*900));level.getChunkAt(p);level.setBlock(p,SplineRoads.NODE.get().defaultBlockState(),3);((NodeEntity)level.getBlockEntity(p)).owner=new UUID(0,0);points[i]=p.asLong();}
  var payload=Interchanges.payload(level,null,points,null);var settings=new Settings(Mode.STRAIGHT,Style.H4_RAIL,Style.H4_RAIL.defaultWidth(),1,.4,90);
  for(String key:List.of("Main1","Main2","Main3"))payload.put(key,RoadRecord.writeSettings(settings));
  payload.put("Options",Interchanges.write(new Options(Preset.DIRECTIONAL_SIX,false,1,96,20,5,2,5,true,true,0)));
  var plan=Interchanges.plan(payload);Set<Long> apron=new HashSet<>();for(var leg:plan.legs())apron.addAll(RoadCoverage.chunks(leg.mesh(),8));
  h.assertTrue(apron.size()>1000,"fixture exercises a full minimum six-way footprint");
  String result=Interchanges.build(level,null,payload);System.out.println("SIX-WAY REAL BUILD: "+result+", old apron chunks="+apron.size());
  var desc=data.interchanges.values().stream().filter(t->t.getLongArray("Points").length==6).findFirst().orElseThrow();UUID id=desc.getUUID("Id");
  var roads=data.index.roads.values().stream().filter(r->id.equals(r.record.assembly())).toList();
  h.assertTrue(roads.size()==plan.legs().size(),"every physical leg committed in one transaction");
  h.assertTrue(RoadWorkChunks.heldCount(level)==0,"all work chunk tickets released on success");
  var saved=RoadData.load(data.save(new CompoundTag()));for(var road:roads)h.assertTrue(saved.index.roads.get(road.record.id()).mesh.samples().equals(road.mesh.samples()),"grouped geometry survives save and reload");
  var fitted=desc.getList("Nodes",Tag.TAG_COMPOUND);
  double ab=RoadRecord.readNode(fitted.getCompound(0)).position().y(),cd=RoadRecord.readNode(fitted.getCompound(2)).position().y(),ef=RoadRecord.readNode(fitted.getCompound(4)).position().y();
  h.assertTrue(ab>cd&&cd>ef&&ef==90,"equal-height automatic adjustment raises AB first, then CD, retaining the lowest level");
  h.succeed();
 }
}
