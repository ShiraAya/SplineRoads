package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;

@GameTestHolder("splineroads_regression15")
@PrefixGameTestTemplate(false)
public final class Revision15GameTests {
  private static BlockPos node(ServerLevel level, BlockPos p) {
    level.getChunkAt(p);
    level.setBlockAndUpdate(p, SplineRoads.NODE.get().defaultBlockState());
    var n=(NodeEntity)level.getBlockEntity(p);n.heightExplicit=true;n.offsetY=.25;n.setChanged();
    return p;
  }
  private static Settings settings(Style s) {
    return new Settings(Mode.AUTO,s,s.defaultWidth(),1,.4,90);
  }

  /** Called on actual ordinary and highway assemblies, including block-edge width padding. */
  static void extendAssembly(GameTestHelper h,BlockPos at,BlockPos to) {
    var level=h.getLevel();var data=RoadData.get(level);
    var host=data.index.roads.get(data.index.atNode(at).iterator().next());
    var s=settings(host.record.settings().style());node(level,to);
    var payload=new CompoundTag();data.jointPayload(payload,at,to,null);
    var preview=RoadPlanner.plan(data.hint(level,at,true,null),data.hint(level,to,false,null),
        RoadData.joinSections(s,payload));
    var road=data.connect(level,null,at,to,s,null);
    var actual=data.index.roads.get(road.id());
    h.assertTrue(Math.abs(preview.mesh().first().halfWidth()-actual.mesh.first().halfWidth())<1e-8,
        "valid assembly continuation preview matches the built width");
    h.assertTrue(Math.abs(actual.mesh.first().halfWidth()-host.mesh.first().halfWidth())<1e-8,
        "actual assembly endpoint width is inherited without another grid-padding pass");
    h.assertTrue(actual.mesh.first().center().distance(host.mesh.first().center())<1e-8,
        "actual assembly continuation shares the exact point");
    data.remove(level,null,road.id());
    level.setBlockAndUpdate(to,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
    h.assertTrue(!(level.getBlockEntity(to) instanceof NodeEntity),
        "temporary extension marker is removed after the regression");
  }

  @GameTest(template="empty",batch="regressions15",timeoutTicks=600)
  public static void furnitureContinuesAndPersists(GameTestHelper h) {
    var level=h.getLevel();var data=RoadData.get(level);
    int x=43000,z=43000;
    var a=node(level,new BlockPos(x,110,z));var b=node(level,a.offset(0,0,73));
    var c=node(level,b.offset(0,0,47));var d=node(level,c.offset(0,0,91));
    var s=settings(Style.O4_RAIL);
    var first=data.connect(level,null,a,b,s,null);
    var second=data.connect(level,null,b,c,s,null);
    var third=data.connect(level,null,d,c,s,null); // Reversed construction order/direction.
    var ids=List.of(first.id(),second.id(),third.id());
    for(boolean piers:new boolean[]{false,true}) {
      List<Double> stations=new ArrayList<>();
      for(var id:ids)for(var part:data.index.roads.get(id).record.structures())
        if(piers ? part.pier() && part.width() > 1.4 : part.luminous() && part.a().x()>x+.5)
          stations.add(part.a().add(part.b()).mul(.5).z()-z-.5);
      Collections.sort(stations);
      h.assertTrue(stations.size()>=8,"fixtures exercise multiple structures on every section");
      for(int i=1;i<stations.size();i++)h.assertTrue(Math.abs(stations.get(i)-stations.get(i-1)-24)<.02,
          (piers?"piers":"lamps")+" keep 24-block spacing across real seams: "+stations);
    }
    for(var id:ids) {
      var r=data.index.roads.get(id).record;var copy=RoadRecord.load(r.save());
      h.assertTrue(copy.furniturePhase().equals(r.furniturePhase()),"station origin/direction survives NBT");
      h.assertTrue(copy.structures().equals(r.structures()),"placed furniture survives NBT");
      var draft=new RoadRecord(UUID.randomUUID(),r.owner(),r.a(),r.b(),r.start(),r.end(),r.settings());
      var inherited=FurnitureSpacing.resolve(draft,draft.mesh(),data.index.roads.values().stream()
          .filter(o->!o.record.id().equals(r.id())).toList());
      h.assertTrue(inherited.direction()==r.furniturePhase().direction()
          && Math.abs(inherited.origin()-r.furniturePhase().origin())<1e-6,
          "client preview derives the same phase from the neighbor");
    }
    var before=data.index.roads.get(second.id()).record.furniturePhase();
    data.connect(level,null,b,c,s,second.id());
    h.assertTrue(data.index.roads.get(second.id()).record.furniturePhase().equals(before),
        "rebuilding a segment preserves its origin");
    for(var id:ids)data.remove(level,null,id);
    h.assertTrue(RoadWorkChunks.heldCount(level)==0,"furniture rebuild releases chunks");
    h.succeed();
  }
}
