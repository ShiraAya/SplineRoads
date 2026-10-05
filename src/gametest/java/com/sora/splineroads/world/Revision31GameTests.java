package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadInfrastructure.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_revision31") @PrefixGameTestTemplate(false)
public final class Revision31GameTests {
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void underwaterNegativeDiagonalTunnel(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);
    for(int x=30000;x<=30064;x++)for(int z=29986;z<=30064;z++)for(int y=-22;y<=-2;y++){
      var p=new BlockPos(x,y,z);level.getChunkAt(p);level.setBlock(p,(y<-14||x<30024||x>30038?Blocks.DEEPSLATE:Blocks.WATER).defaultBlockState(),2);
    }
    var a=Revision28GameTests.marker(h,30010,-18,30000);var b=Revision28GameTests.marker(h,30050,-16,30040);
    for(var pos:List.of(a,b)){var n=(NodeEntity)level.getBlockEntity(pos);n.apply(new Node(n.node().position(),-45,0));}
    var s=Revision28GameTests.road(Style.O2_YELLOW,Structure.TUNNEL,Config.DEFAULT.gantry(Gantry.OFF));
    var r=data.connect(level,null,a,b,s,null);var built=data.index.roads.get(r.id());
    Runnable check=()->{for(double d=5;d<built.mesh.length()-5;d+=2){var sample=RoadStructures.sample(built.mesh,d);for(int side:new int[]{-1,1})for(int y=1;y<=5;y++){
      var v=sample.at(side*sample.halfWidth()*.65,-y);var p=BlockPos.containing(v.x(),v.y(),v.z());
      h.assertTrue((level.getBlockState(p).isAir()||RoadBlocks.isCollider(level.getBlockState(p))&&level.getBlockState(p).getValue(RoadBlocks.Road.FILL)==RoadBlocks.Fill.NONE)&&level.getFluidState(p).isEmpty(),"dry excavated interior at "+p+" got "+level.getBlockState(p));
    }}};
    check.run();
    var at=RoadStructures.sample(built.mesh,built.mesh.length()/2).at(built.mesh.first().halfWidth()*.55,-2);var inside=BlockPos.containing(at.x(),at.y(),at.z());
    h.assertTrue(level.getBlockState(inside).is(com.sora.splineroads.SplineRoads.TUNNEL_AIR.get()),"excavated dry air is persistent");
    h.assertTrue(!((TunnelAir)com.sora.splineroads.SplineRoads.TUNNEL_AIR.get()).canPlaceLiquid(level,inside,level.getBlockState(inside),net.minecraft.world.level.material.Fluids.WATER),"water cannot refill clearance");
    h.runAfterDelay(120,()->{check.run();data.remove(level,null,r.id());h.assertTrue(level.getBlockState(inside).is(Blocks.DEEPSLATE)||level.getBlockState(inside).is(Blocks.WATER),"dry air removal restores original terrain/water");h.succeed();});
  }

  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void signsEditRoundTripAndRemove(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var a=Revision28GameTests.marker(h,31000,12,31000);var b=Revision28GameTests.marker(h,31120,12,31000);
    var settings=Revision28GameTests.road(Style.O4_YELLOW,Structure.GROUND,Config.DEFAULT.gantry(Gantry.FRAME));var r=data.connect(level,null,a,b,settings,null);
    var sign=new RoadSigns.Attachment(0,"sign_expressway_distance_from_location_1",RoadSigns.Mount.GANTRY,0,.5,0,5.6,1,false,List.of("東京","南城","North","2","3","4"));
    var t=new net.minecraft.nbt.CompoundTag();t.putUUID("Id",r.id());t.putInt("Signature",GantryTool.signature(r));t.putBoolean("New",true);t.put("Attachment",RoadSignCodec.write(sign));data.editSign(level,null,t);
    var edited=data.index.roads.get(r.id()).record;h.assertTrue(edited.settings().options().infrastructure().signs().equals(List.of(sign)),"text saved in road");h.assertTrue(edited.structures().stream().anyMatch(p->p.material()==RoadStructures.Material.CB_SIGN),"CB model installed on gantry");
    var saved=data.save(new net.minecraft.nbt.CompoundTag());h.assertTrue(RoadData.load(saved).index.roads.get(r.id()).record.equals(edited),"world round trip with CB mesh and UTF8 text");
    boolean rejected=false;try{data.editSign(level,null,t);}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected&&data.save(new net.minecraft.nbt.CompoundTag()).equals(saved),"stale edit rejected atomically");
    var pole=new RoadSigns.Attachment(1,"road_detection_camera",RoadSigns.Mount.POLE,0,.25,-2,3,1,false,List.of());t.putInt("Signature",GantryTool.signature(edited));t.put("Attachment",RoadSignCodec.write(pole));data.editSign(level,null,t);
    edited=data.index.roads.get(r.id()).record;h.assertTrue(edited.settings().options().infrastructure().signs().size()==2,"independent roadside pole and overhead sign");
    t.putInt("Signature",GantryTool.signature(edited));t.putBoolean("New",false);t.putBoolean("Delete",true);data.editSign(level,null,t);edited=data.index.roads.get(r.id()).record;h.assertTrue(edited.settings().options().infrastructure().signs().equals(List.of(sign)),"deleting pole preserves other sign");
    data.remove(level,null,r.id());h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision28",timeoutTicks=12000)
  public static void sidewalkMigrationCurveSlope(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var a=Revision28GameTests.marker(h,32000,6,32000);var b=Revision28GameTests.marker(h,32070,10,32030);
    var old=new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,3,"minecraft:bricks",false);
    var settings=Revision28GameTests.road(Style.O2_YELLOW,Structure.GROUND,Config.DEFAULT.gantry(Gantry.OFF));settings=settings.options(settings.options().sidewalk(old));
    var r=data.connect(level,null,a,b,settings,null);var legacy=SmartSidewalks.plan(List.of(data.index.roads.get(r.id())));h.assertTrue(!legacy.isEmpty(),"legacy brick sidewalk exists");
    settings=settings.options(settings.options().sidewalk(old.smooth(true)));r=data.connect(level,null,a,b,settings,r.id());var built=data.index.roads.get(r.id());
    h.assertTrue(SmartSidewalks.plan(List.of(built)).isEmpty(),"new sidewalk never leaves voxel block placements");h.assertTrue(r.structures().stream().anyMatch(p->p.material()==RoadStructures.Material.WALK_BRICKS),"SR brick geometry");h.assertTrue(r.structures().stream().anyMatch(p->p.material()==RoadStructures.Material.TACTILE),"automatic tactile paving");
    for(var p:r.structures())if(p.material()==RoadStructures.Material.WALK_BRICKS){var mid=p.a().add(p.b()).mul(.5).add(new V(0,.25,0));var block=BlockPos.containing(mid.x(),mid.y(),mid.z());h.assertTrue(RoadBlocks.isCollider(level.getBlockState(block)),"continuous walk has world collision");}
    h.assertTrue(RoadData.load(data.save(new net.minecraft.nbt.CompoundTag())).index.roads.get(r.id()).record.equals(r),"smooth sidewalk survives world save");data.remove(level,null,r.id());
    for(long key:legacy.keySet())h.assertTrue(!level.getBlockState(BlockPos.of(key)).is(Blocks.BRICKS),"legacy bricks removed/restored with whole road");h.succeed();
  }
}
