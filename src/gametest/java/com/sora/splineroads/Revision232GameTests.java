package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("splineroads_revision232") @PrefixGameTestTemplate(false)
public final class Revision232GameTests {
  static BlockPos node(GameTestHelper h,int x,int z){BlockPos p=new BlockPos(x,90,z);h.getLevel().setBlock(p,SplineRoads.NODE.get().defaultBlockState(),3);((NodeEntity)h.getLevel().getBlockEntity(p)).owner=new UUID(0,0);return p;}
  static Settings settings(){return new Settings(Mode.STRAIGHT,Style.O2_YELLOW,9,1,.35,90).structure(Structure.GROUND).options(RoadProfile.Options.DEFAULT.sidewalk(RoadSidewalks.Config.DEFAULT.enabled(true)));}
  static BlockState material(GameTestHelper h,BlockPos p){
    var state=h.getLevel().getBlockState(p);
    if(h.getLevel().getBlockEntity(p) instanceof RoadFillEntity fill)return fill.fill();
    return RoadBlocks.isCollider(state)?state.getValue(RoadBlocks.Road.FILL).state():state;
  }
  static void assertSurface(GameTestHelper h,BlockPos p,BlockState expected){
    h.assertTrue(material(h,p).equals(expected),"sidewalk surface has selected material at "+p+", actual "+material(h,p));
    h.assertTrue(!Shapes.joinIsNotEmpty(Shapes.block(),h.getLevel().getBlockState(p).getCollisionShape(h.getLevel(),p),BooleanOp.ONLY_FIRST),"no walking collision gap at "+p);
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision232",timeoutTicks=1600)
  public static void fractionalFiveBlockSidewalkMaterialAndRestoration(GameTestHelper h){
    var l=h.getLevel();var d=RoadData.get(l);int ox=-59000,oz=-59000;
    for(int x=ox-12;x<=ox+72;x++)for(int z=oz-12;z<=oz+52;z++)l.setBlock(new BlockPos(x,89,z),Blocks.AIR.defaultBlockState(),2);
    var a=node(h,ox,oz);var b=node(h,ox+60,oz+40);Settings s=settings();
    var road=d.connect(l,null,a,b,s,null);var built=d.index.roads.get(road.id());var plan=SmartSidewalks.plan(List.of(built));
    var edge=plan.keySet().stream().filter(built.cells::containsKey).map(BlockPos::of).toList();
    h.assertTrue(edge.size()>30,"fractional diagonal edge cells are retained, not discarded");
    for(var p:edge){assertSurface(h,p,Blocks.STONE_BRICKS.defaultBlockState());h.assertTrue(l.getBlockEntity(p)==null,"default stone-brick edge is a baked block, not a ticking/rendering entity");}
    for(double distance=3;distance<built.mesh.length()-3;distance+=.55)for(int side:new int[]{-1,1})for(double width:new double[]{.05,.95,1.95,2.95,3.95,4.95}){
      var sample=RoadStructures.sample(built.mesh,distance);V at=sample.at(side*(sample.halfWidth()+width),0);
      assertSurface(h,BlockPos.containing(at.x(),89,at.z()),Blocks.STONE_BRICKS.defaultBlockState());
    }
    var saved=RoadData.load(d.save(new CompoundTag()));
    h.assertTrue(saved.save(new CompoundTag()).getList("SidewalkPalette",Tag.TAG_COMPOUND).stream().mapToInt(t->((CompoundTag)t).getLongArray("Positions").length).sum()==d.save(new CompoundTag()).getList("SidewalkPalette",Tag.TAG_COMPOUND).stream().mapToInt(t->((CompoundTag)t).getLongArray("Positions").length).sum(),"partial-cell ownership survives data reload");
    // Generic materials share one road block state, so changing material must still update its entity.
    for(var block:List.of(Blocks.GRAY_CONCRETE,Blocks.GREEN_CONCRETE,Blocks.BRICKS)){
      var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();s=s.options(s.options().sidewalk(s.options().sidewalk().material(id)));
      d.connect(l,null,a,b,s,road.id());
      for(var p:edge)assertSurface(h,p,block.defaultBlockState());
    }
    Settings off=s.options(s.options().sidewalk(s.options().sidewalk().enabled(false)));
    d.connect(l,null,a,b,off,road.id());
    for(var p:edge){h.assertTrue(RoadBlocks.isCollider(l.getBlockState(p)),"disabling sidewalk retains independent road deck");h.assertTrue(l.getBlockState(p).getValue(RoadBlocks.Road.FILL)==RoadBlocks.Fill.NONE,"disabling restores original air below partial edge");}
    d.connect(l,null,a,b,s,road.id());d.remove(l,null,road.id());
    for(var p:edge)h.assertTrue(l.getBlockState(p).isAir(),"deleting automatic infill restores pre-sidewalk terrain, not last sidewalk material");
    h.assertTrue(RoadWorkChunks.heldCount(l)==0,"work chunks released");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision232",timeoutTicks=2400)
  public static void automaticUnequalJunctionCornerPavingAndSignals(GameTestHelper h){
    var l=h.getLevel();var d=RoadData.get(l);var c=node(h,59000,59000);var a=node(h,59110,59000);var b=node(h,59000,59110);var e=node(h,58890,59000);
    var s=settings();var wide=new Settings(Mode.STRAIGHT,Style.O4_YELLOW,17,1,.35,90).structure(Structure.GROUND).options(s.options());
    var first=d.connect(l,null,c,a,s,null);var second=d.connect(l,null,c,b,wide,null,false,true);var third=d.connect(l,null,c,e,s,null,false,true);
    var approach=d.index.roads.values().stream().filter(r->r.record.junction()!=null&&(r.record.a().equals(c)||r.record.b().equals(c))).findFirst().orElseThrow();
    var edit=Junctions.payload(l,null,new long[0],approach.record.assembly());edit.getCompound("Spec").putString("Control","SIGNALS");Junctions.build(l,null,edit);
    var roads=d.index.roads.values().stream().filter(r->r.record.junction()!=null&&r.record.assembly().equals(approach.record.assembly())).toList();
    var plan=JunctionPlanner.plan(roads.get(0).record.junction().spec());h.assertTrue(!plan.sidewalks().isEmpty(),"junction owns curved-corner sidewalks");
    int checked=0;
    for(var cell:plan.sidewalks().keySet()) {
      V at=new V(cell.x()+.5,90,cell.z()+.5);
      if(plan.pieces().stream().anyMatch(p->RoadQueries.contains(p.mesh(),at,.1,.2)))continue;
      assertSurface(h,new BlockPos(cell.x(),cell.y(),cell.z()),Blocks.STONE_BRICKS.defaultBlockState());checked++;
    }
    h.assertTrue(checked>40,"world contains actual blocks around previously empty corners");
    var structures=roads.stream().flatMap(r->r.record.structures().stream()).toList();int shared=0;
    for(var head:structures)if(head.material()==RoadStructures.Material.SIGNAL_PEDESTRIAN){
      V front=head.b().sub(head.a()).horizontalUnit(),base=head.a().sub(front.mul(.19)).sub(new V(0,2.3,0));
      var post=structures.stream().filter(p->p.material()==RoadStructures.Material.CB_POST&&p.a().add(p.b()).mul(.5).distance(base)<1e-6).findFirst();
      h.assertTrue(post.isPresent(),"built pedestrian light physically mounted on a post");if(post.orElseThrow().height()>6)shared++;
    }
    h.assertTrue(shared>0,"near pedestrian heads reuse built vehicle masts");
    var reload=RoadData.load(d.save(new CompoundTag()));
    for(var r:roads)h.assertTrue(reload.index.roads.get(r.record.id()).record.structures().equals(r.record.structures()),"shared signal layouts persist after reload");
    d.remove(l,null,third.id());d.remove(l,null,second.id());d.remove(l,null,first.id());
    h.assertTrue(RoadWorkChunks.heldCount(l)==0,"junction chunks released");h.succeed();
  }
}
