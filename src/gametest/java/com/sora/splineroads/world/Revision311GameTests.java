package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadInfrastructure.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.SplineRoads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_revision311") @PrefixGameTestTemplate(false)
public final class Revision311GameTests {
  @GameTest(template="empty",templateNamespace="splineroads_revision311",timeoutTicks=12000)
  public static void wideSlopingAquariumTunnelAndRealFluidTicks(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);
    for(int x=34000;x<=34070;x++)for(int z=33975;z<=34055;z++)for(int y=-20;y<=-2;y++){var p=new BlockPos(x,y,z);level.getChunkAt(p);level.setBlock(p,Blocks.WATER.defaultBlockState(),2);}
    var a=Revision28GameTests.marker(h,34015,-18,34000);var b=Revision28GameTests.marker(h,34055,-17,34030);
    for(var pos:List.of(a,b)){var n=(NodeEntity)level.getBlockEntity(pos);n.apply(new Node(n.node().position(),-Math.toDegrees(Math.atan2(40,30)),0));}
    var s=Revision28GameTests.road(Style.O6_YELLOW,Structure.TUNNEL,Config.DEFAULT.gantry(Gantry.OFF).tunnel(Tunnel.ARCH).headroom(8));
    var r=data.connect(level,null,a,b,s,null);var built=data.index.roads.get(r.id());var wetOutside=new HashSet<BlockPos>();var protectedCells=new HashSet<BlockPos>();
    // Test the complete lining and its surroundings, not just the central lane at eye height.
    for(var part:r.structures())if(part.material()==Material.TUNNEL)for(var cell:RoadRaster.structures(List.of(part),null).keySet()){
      var p=new BlockPos(cell.x(),cell.y(),cell.z());protectedCells.add(p);for(var direction:Direction.values())if(!level.getFluidState(p.relative(direction)).isEmpty())wetOutside.add(p.relative(direction));
      h.assertTrue(level.getFluidState(p).isEmpty()&&RoadBlocks.isCollider(level.getBlockState(p)),"lining voxel evacuated "+p);
      h.assertTrue(!((RoadBlocks.Road)level.getBlockState(p).getBlock()).canPlaceLiquid(level,p,level.getBlockState(p),Fluids.WATER),"thin lining explicitly rejects fluid "+p);
    }
    for(long key:built.clearanceCells){var p=BlockPos.of(key);protectedCells.add(p);h.assertTrue(level.getFluidState(p).isEmpty(),"full excavation, including lining boundary, dry "+p);}
    // Remote test coordinates do not get ordinary random chunk ticks: invoke the real fluid
    // tick explicitly, so the test genuinely exercises spreading and not just elapsed time.
    var seeds=wetOutside.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).limit(180).toList();h.assertTrue(!seeds.isEmpty(),"aquarium supplies exterior water pressure");
    for(int round=0;round<12;round++){var positions=new HashSet<>(seeds);for(var p:seeds)for(var d:Direction.values())positions.add(p.relative(d));for(var p:positions){var fluid=level.getFluidState(p);if(!fluid.isEmpty())fluid.tick(level,p);}}
    for(var p:protectedCells)h.assertTrue(level.getFluidState(p).isEmpty(),"ordinary fluid ticks cannot replace dry interior or shell "+p);
    var inside=BlockPos.containing(RoadStructures.sample(built.mesh,built.mesh.length()/2).center().x(),-12,RoadStructures.sample(built.mesh,built.mesh.length()/2).center().z());data.remove(level,null,r.id());h.assertTrue(level.getBlockState(inside).is(Blocks.WATER),"removal restores aquarium water");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision311",timeoutTicks=12000)
  public static void diagonalSidewalkRetainsTerrainAtOuterEdge(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);for(int x=35000;x<35065;x++)for(int z=34985;z<35065;z++){var p=new BlockPos(x,1,z);level.getChunkAt(p);level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);}
    var a=Revision28GameTests.marker(h,35010,2,35000);var b=Revision28GameTests.marker(h,35050,2,35040);for(var pos:List.of(a,b)){var n=(NodeEntity)level.getBlockEntity(pos);n.apply(new Node(n.node().position(),-45,0));}
    var s=Revision28GameTests.road(Style.O2_YELLOW,Structure.GROUND,Config.DEFAULT.gantry(Gantry.OFF));s=s.options(s.options().sidewalk(new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks")));var r=data.connect(level,null,a,b,s,null);var built=data.index.roads.get(r.id());built.cells.size();int checked=0;
    for(var e:built.walkTops.entrySet()){var p=BlockPos.of(e.getKey());if(p.getY()!=1||built.column(p)!=null)continue;var state=level.getBlockState(p);h.assertTrue(RoadBlocks.isCollider(state)&&state.getValue(RoadBlocks.Road.FILL)!=RoadBlocks.Fill.NONE,"original grass retained in sidewalk boundary cell "+p);checked++;}
    h.assertTrue(checked>40,"diagonal sidewalk boundary inspected");data.remove(level,null,r.id());h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision311",timeoutTicks=12000)
  public static void joinedSidewalksKeepBothEnds(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var a=Revision28GameTests.marker(h,36000,4,36000);var b=Revision28GameTests.marker(h,36030,4,36030);var c=Revision28GameTests.marker(h,36060,4,36060);
    for(var pos:List.of(a,b,c)){var n=(NodeEntity)level.getBlockEntity(pos);n.apply(new Node(n.node().position(),-45,0));}
    var s=Revision28GameTests.road(Style.O2_YELLOW,Structure.GROUND,Config.DEFAULT.gantry(Gantry.OFF));s=s.options(s.options().sidewalk(new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks")));var r1=data.connect(level,null,a,b,s,null);var r2=data.connect(level,null,b,c,s,null);
    for(var road:List.of(data.index.roads.get(r1.id()),data.index.roads.get(r2.id())))for(int side:new int[]{-1,1})for(double d:new double[]{.2,.6,road.mesh.length()-.6,road.mesh.length()-.2}){
      var sample=RoadStructures.sample(road.mesh,d);var at=sample.at(side*(sample.halfWidth()+2.5),0);h.assertTrue(road.record.structures().stream().filter(p->p.material()==Material.WALK_STONE_BRICKS).anyMatch(p->JunctionPaint.inside(p.base(),at)),"continuous sidewalk at both road ends "+d+" side "+side);
    }
    data.remove(level,null,r1.id());data.remove(level,null,r2.id());h.succeed();
  }
}
