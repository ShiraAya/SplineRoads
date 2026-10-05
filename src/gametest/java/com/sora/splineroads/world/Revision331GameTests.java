package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.SplineRoads;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_revision331") @PrefixGameTestTemplate(false)
public final class Revision331GameTests {
 static Settings road(Style s){return Revision32GameTests.road(s,Structure.GROUND);}
 static BlockPos marker(GameTestHelper h,int x,int z,double yaw){return Revision32GameTests.marker(h,x,2,z,yaw);}
 static void noCycle(GameTestHelper h,RoadIndex.Built b){for(var s:b.mesh.samples())h.assertTrue(RoadProfile.layout(b.mesh,s).cycleWidth()<1e-8,"disabled cycle lane has no residual taper at "+s.distance());}
 @GameTest(template="empty",templateNamespace="splineroads_revision331",timeoutTicks=12000)
 public static void disablingCycleClearsBothSidesWithoutChangingIndependentRoad(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);var a=marker(h,50000,50000,-90);var b=marker(h,50080,50000,-90);var c=marker(h,50160,50000,-90);
  var u=marker(h,50000,50040,-90);var v=marker(h,50160,50040,-90);var s=road(Style.O4_YELLOW);
  var first=d.connect(l,null,a,b,s,null);var second=d.connect(l,null,b,c,s,null);var independent=d.connect(l,null,u,v,s,null);var exact=d.index.roads.get(independent.id());var saved=exact.record.save();
  for(int iteration=0;iteration<3;iteration++){
   var current=d.index.roads.get(first.id()).record.settings();var on=current.options(current.options().extras(true,true,true));
   d.connect(l,null,a,b,on,first.id());h.assertTrue(d.index.roads.get(second.id()).record.settings().options().ends().start().cycle(),"connected seam really gains cycle lane");
   current=d.index.roads.get(first.id()).record.settings();d.connect(l,null,a,b,current.options(current.options().extras(false,false,false)),first.id());
   noCycle(h,d.index.roads.get(first.id()));noCycle(h,d.index.roads.get(second.id()));
   h.assertTrue(d.index.roads.get(independent.id())==exact&&exact.record.save().equals(saved),"independent road keeps exact object and persisted bytes");
  }
  var reload=RoadData.load(d.save(new CompoundTag()));noCycle(h,reload.index.roads.get(first.id()));noCycle(h,reload.index.roads.get(second.id()));
  for(var r:List.of(first,second,independent))d.remove(l,null,r.id());h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_revision331",timeoutTicks=12000)
 public static void junctionTrianglesNeverBecomeEditorJoinProfiles(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);var a=marker(h,51000,51000,-90);var b=marker(h,51100,51000,-90);var c=marker(h,51100,51100,0);var s=road(Style.O4_YELLOW);
  var first=d.connect(l,null,a,b,s,null);var second=d.connect(l,null,b,c,s,null,false,true,false);
  h.assertTrue(d.index.roads.values().stream().anyMatch(r->r.record.junction()!=null&&r.mesh.first().halfWidth()==0),"fixture contains zero-width triangle tips from crash path");
  for(var pos:List.of(a,b,c))for(var exclude:List.of(first.id(),second.id())){
   var t=new CompoundTag();d.jointPayload(t,pos,pos,exclude);
   for(String side:List.of("A","B"))if(t.contains("JoinSection"+side))RoadRecord.readSettings(t.getCompound("JoinSection"+side)).validate();
  }
  var current=d.streets.get(first.id()).settings();d.connect(l,null,a,b,current.options(current.options().extras(true,false,false)),first.id());
  current=d.streets.get(first.id()).settings();d.connect(l,null,a,b,current.options(current.options().extras(false,false,false)),first.id());
  noCycle(h,d.index.roads.get(first.id()));noCycle(h,d.index.roads.get(second.id()));
  for(var config:d.junctions.values())if(config.getLong("CenterPos")==b.asLong())for(var arm:JunctionCodec.read(config.getCompound("Spec")).arms())h.assertTrue(arm.cycleWidth()==0,"junction must not inherit stale cycle taper");
  d.remove(l,null,first.id());d.remove(l,null,second.id());h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_revision331",timeoutTicks=12000)
 public static void legacyProfilesKeepSidewalksThroughBuiltY(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);var a=marker(h,52000,52000,180);var b=marker(h,52028,51900,180);var c=marker(h,51972,51900,0);
  var aa=marker(h,52000,52040,180);var bb=marker(h,52028,51860,180);var cc=marker(h,51972,51860,0);
  var walk=new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks");var main=road(Style.O4_YELLOW);main=main.options(main.options().sidewalk(walk));var one=road(Style.O2_ONE);one=one.options(one.options().sidewalk(walk));
  var ra=d.connect(l,null,aa,a,main,null);var rb=d.connect(l,null,b,bb,one,null);var rc=d.connect(l,null,cc,c,one,null);
  // Reproduce an older saved automatic seam whose NBT predates the Sidewalk field.
  for(var r:List.of(ra,rb,rc)){
   var s=r.settings();var legacy=new RoadTransitions.Section(s.style(),s.width(),s.options().cycle(),s.options().cycleRail(),s.options().curb(),s.options().outerRail());
   d.index.put(new RoadIndex.Built(r.settings(RoadTransitions.ends(s,legacy,legacy))));
  }
  long[] points={a.asLong(),b.asLong(),c.asLong()};var stack=new ItemStack(SplineRoads.Y_JUNCTION.get());stack.getOrCreateTag().putLongArray("Points",points);stack.getOrCreateTag().putString("Dimension",l.dimension().location().toString());
  var payload=YJunctionTool.payload(l,null,points,null);
  for(int i=0;i<3;i++)h.assertTrue(RoadRecord.readSettings(payload.getCompound("Profile"+i)).options().sidewalk().enabled(),"Y endpoint "+i+" inherits sidewalk from legacy host");
  YJunctionTool.build(l,null,payload,stack);var group=d.interchanges.entrySet().stream().filter(e->Arrays.equals(e.getValue().getLongArray("Points"),points)).findFirst().orElseThrow().getKey();
  for(int pass=0;pass<2;pass++){
   for(var r:d.index.roads.values())if(group.equals(r.record.assembly())){
    h.assertTrue(r.record.settings().options().sidewalk().enabled(),"all three Y pieces keep sidewalk");
    var m=r.mesh;int side=1;
    for(double t:new double[]{.05,.25,.5,.75,.95}){
     var at=RoadStructures.sample(m,m.length()*t);V point=at.at(at.halfWidth()+2.5,-.1);
     h.assertTrue(r.record.structures().stream().filter(p->p.material().name().startsWith("WALK_")).anyMatch(p->JunctionPaint.inside(p.base(),point)),"Y exterior sidewalk has no gap at "+t);
    }
   }
   var edit=YJunctionTool.payload(l,null,points,group);edit.putDouble("Tension",.45);YJunctionTool.build(l,null,edit,stack);
  }
  var reload=RoadData.load(d.save(new CompoundTag()));h.assertTrue(reload.index.roads.values().stream().filter(r->group.equals(r.record.assembly())).allMatch(r->r.record.settings().options().sidewalk().enabled()),"Y walks survive reload");
  Interchanges.remove(l,null,group);for(var r:List.of(ra,rb,rc))d.remove(l,null,r.id());h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_revision331",timeoutTicks=12000)
 public static void waterFillsAroundPiersAndBeamsWithoutFloodingTunnels(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);
  for(int x=52992;x<=53070;x++)for(int z=52984;z<=53016;z++)for(int y=0;y<=10;y++){
   var p=new BlockPos(x,y,z);l.getChunkAt(p);l.setBlock(p,(y==0?net.minecraft.world.level.block.Blocks.STONE:net.minecraft.world.level.block.Blocks.WATER).defaultBlockState(),2);
  }
  var a=Revision32GameTests.marker(h,53000,14,53000,-90);var b=Revision32GameTests.marker(h,53060,14,53000,-90);
  var bridge=d.connect(l,null,a,b,road(Style.O4_YELLOW).structure(Structure.BRIDGE),null);var built=d.index.roads.get(bridge.id());
  // Sample every occupied structural cell below the slab, including thin horizontal caps/beams.
  var wet=new ArrayList<BlockPos>();int pierCells=0,beamCells=0;
  for(var part:built.record.structures())if(part.material()==Material.CONCRETE){
   for(var cell:RoadRaster.structures(List.of(part),null).keySet()){
    if(cell.y()<1||cell.y()>10)continue;var p=new BlockPos(cell.x(),cell.y(),cell.z());var state=l.getBlockState(p);
    if(!RoadBlocks.isCollider(state))continue;
    h.assertTrue(state.getValue(RoadBlocks.Road.WATERLOGGED)&&l.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER),"water meets structure inside boundary voxel "+p);
    wet.add(p);if(part.pier())pierCells++;else beamCells++;
   }
  }
  h.assertTrue(pierCells>0,"wet vertical supports exercised");
  // The beam is above the first pool's surface. Raise water into a non-deck beam voxel,
  // using the same fluid insertion that vanilla fluid ticks call.
  for(var part:built.record.structures())if(!part.pier()&&part.material()==Material.CONCRETE)
   for(var cell:RoadRaster.structures(List.of(part),null).keySet()){
    var p=new BlockPos(cell.x(),cell.y(),cell.z());var state=l.getBlockState(p);
    if(!RoadBlocks.isCollider(state)||!state.getValue(RoadBlocks.Road.PERMEABLE)||state.getValue(RoadBlocks.Road.WATERLOGGED))continue;
    var block=(RoadBlocks.Road)state.getBlock();h.assertTrue(block.placeLiquid(l,p,state,net.minecraft.world.level.material.Fluids.WATER.getSource(false)),"water enters free part of beam cell");
    h.assertTrue(l.getFluidState(p).isSource(),"beam cell contains visible water");beamCells++;break;
   }
  h.assertTrue(beamCells>0,"horizontal beams exercised");
  var p=wet.get(0);for(int round=0;round<8;round++)l.getFluidState(p).tick(l,p);
  h.assertTrue(RoadBlocks.isCollider(l.getBlockState(p))&&!l.getFluidState(p).isEmpty(),"fluid ticking preserves structure and water");
  var updated=d.connect(l,null,a,b,d.index.roads.get(bridge.id()).record.settings(),bridge.id());h.assertTrue(l.getBlockState(p).getValue(RoadBlocks.Road.WATERLOGGED),"editing bridge preserves water around pier");
  d.remove(l,null,bridge.id());h.assertTrue(l.getBlockState(p).is(net.minecraft.world.level.block.Blocks.WATER),"removing pier restores original water");h.succeed();
 }

}
