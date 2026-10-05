package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadInfrastructure.*;
import com.sora.splineroads.core.RoadStructures.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("splineroads_revision30") @PrefixGameTestTemplate(false)
public final class Revision30GameTests {
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void longElevatedRoadKeepsPiersNearUnrelatedPoles(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);
    var a=Revision28GameTests.marker(h,21000,18,21000);var b=Revision28GameTests.marker(h,21480,18,21000);
    for(int x=21000;x<=21480;x++)for(int z=20998;z<=21002;z++){var p=new BlockPos(x,1,z);level.getChunkAt(p);level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);}
    var settings=Revision28GameTests.road(Style.O4_YELLOW,Structure.AUTO,Config.DEFAULT.gantry(Gantry.OFF));
    var r=data.connect(level,null,a,b,settings,null);var built=data.index.roads.get(r.id());
    var other=new RoadRecord(UUID.randomUUID(),r.owner(),a.above(30),b.above(30),new Node(r.start().position().add(new V(0,30,0)),-90,0),new Node(r.end().position().add(new V(0,30,0)),-90,0),settings);
    var poles=new ArrayList<Part>();for(int x=21000;x<=21480;x+=4){var p=new V(x,48,21000);poles.add(new Part(p,p,.2,8,true,Material.STEEL));}
    other=other.structures(poles);
    try(var work=RoadWorkChunks.open(level)){
      work.roads(List.of(built));
      var planned=StructurePlanner.plan(level,built,List.of(built,new RoadIndex.Built(other)),new HashMap<>(),new HashMap<>());
      h.assertTrue(planned.structures().stream().filter(p->p.pier()&&p.width()==1.5&&p.height()>3&&p.material()==Material.CONCRETE).count()==20,"lamps on an upper road cannot suppress all piers");
    }
    h.assertTrue(r.structures().stream().filter(p->p.pier()&&p.width()==1.5&&p.height()>3&&p.material()==Material.CONCRETE).count()==20,"all 20 piers exist in saved world geometry");
    data.remove(level,null,r.id());h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void diagonalTunnelRetainsExteriorTerrainAndRestoresIt(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);
    for(int x=22000;x<=22064;x++)for(int z=21986;z<=22064;z++)for(int y=0;y<=8;y++){
      var p=new BlockPos(x,y,z);level.getChunkAt(p);level.setBlock(p,(y==8?Blocks.GRASS_BLOCK:Blocks.STONE).defaultBlockState(),2);
    }
    var a=Revision28GameTests.marker(h,22010,2,22000);var b=Revision28GameTests.marker(h,22050,2,22040);
    for(var pos:List.of(a,b)){var n=(NodeEntity)level.getBlockEntity(pos);n.apply(new Node(n.node().position(),-45,0));}
    var settings=Revision28GameTests.road(Style.O2_YELLOW,Structure.TUNNEL,Config.DEFAULT.gantry(Gantry.OFF));
    var r=data.connect(level,null,a,b,settings,null);var built=data.index.roads.get(r.id());int retained=0;
    for(long key:built.shellCells){var p=BlockPos.of(key);
      if(p.getY()!=8||built.column(p)!=null||p.getX()<22000||p.getX()>22064||p.getZ()<21986||p.getZ()>22064)continue;
      var state=level.getBlockState(p);h.assertTrue(state.getValue(RoadBlocks.Road.FILL)==RoadBlocks.Fill.of(Blocks.GRASS_BLOCK.defaultBlockState()),"diagonal outer boundary keeps full grass, no black voxel wedge");
      h.assertTrue(net.minecraft.world.level.block.Block.isShapeFullBlock(state.getCollisionShape(level,p)),"retained ground is also solid");retained++;
    }
    h.assertTrue(retained>40,"test covers both long diagonal walls");
    for(double d=4;d<built.mesh.length()-4;d+=3){var s=RoadStructures.sample(built.mesh,d);for(int side:new int[]{-1,1}){
      var v=s.at(side*s.halfWidth()*.55,-3);var p=BlockPos.containing(v.x(),v.y(),v.z());h.assertTrue(level.getBlockState(p).isAir(),"terrain retention never blocks interior lanes");
    }}
    var saved=RoadData.load(data.save(new CompoundTag())).index.roads.get(r.id());h.assertTrue(saved.record.structures().equals(r.structures()),"lining is identical after reload");
    data.remove(level,null,r.id());h.assertTrue(level.getBlockState(new BlockPos(22030,8,22020)).is(Blocks.GRASS_BLOCK),"removing tunnel restores original grass");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void automaticBridgeRelocatesOnlySelectedEndAndRejectsObstacles(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);
    var a=Revision28GameTests.marker(h,23000,12,23000);var b=Revision28GameTests.marker(h,23100,12,23000);
    var settings=Revision28GameTests.road(Style.O2_YELLOW,Structure.BRIDGE,Config.DEFAULT.rise(8).adjustment(RoadTunnelFit.Adjustment.END).gantry(Gantry.OFF));
    var aa=((NodeEntity)level.getBlockEntity(a)).constructionNode();var bb=((NodeEntity)level.getBlockEntity(b)).constructionNode();
    var plan=RoadTunnelFit.plan(RoadPlanner.Hint.free(aa),RoadPlanner.Hint.free(bb),settings);var end=plan.end().position();var target=BlockPos.containing(end.x(),end.y(),end.z());
    level.getChunkAt(target);level.setBlock(target,Blocks.CHEST.defaultBlockState(),2);var before=data.save(new CompoundTag());boolean rejected=false;
    try{data.connect(level,null,a,b,settings,null);}catch(IllegalArgumentException e){rejected=true;}
    h.assertTrue(rejected&&before.equals(data.save(new CompoundTag()))&&level.getBlockState(target).is(Blocks.CHEST),"occupied bridge relocation is atomic and preserves chest");
    level.setBlock(target,Blocks.AIR.defaultBlockState(),2);
    var r=data.connect(level,null,a,b,settings,null);var built=data.index.roads.get(r.id());
    h.assertTrue(r.a().equals(a)&&r.start().equals(aa)&&!r.b().equals(b),"only end is extended");
    h.assertTrue(!(level.getBlockEntity(b) instanceof NodeEntity),"old endpoint removed");
    h.assertTrue(RoadGrades.maximum(built.mesh)<=.0601,"actual constructed bridge respects 6%");
    h.assertTrue(Math.abs(built.mesh.samples().stream().mapToDouble(s->s.center().y()).max().orElseThrow()-(Math.max(aa.position().y(),bb.position().y())+8))<1e-7,"bridge reaches requested top");
    h.assertTrue(RoadData.load(data.save(new CompoundTag())).index.roads.get(r.id()).mesh.samples().equals(built.mesh.samples()),"bridge reload exact");
    data.remove(level,null,r.id());h.assertTrue(RoadWorkChunks.heldCount(level)==0,"all tickets released");h.succeed();
  }
}
