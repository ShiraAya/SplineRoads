package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadInfrastructure.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.SplineRoads;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_revision32") @PrefixGameTestTemplate(false)
public final class Revision32GameTests {
  static Settings road(Style style,Structure kind){return Revision28GameTests.road(style,kind,Config.DEFAULT.gantry(Gantry.OFF));}
  static BlockPos marker(GameTestHelper h,int x,int y,int z,double yaw){var p=Revision28GameTests.marker(h,x,y,z);var n=(NodeEntity)h.getLevel().getBlockEntity(p);n.apply(new Node(n.node().position(),yaw,0));return p;}
  @GameTest(template="empty",templateNamespace="splineroads_revision32",timeoutTicks=12000)
  public static void lowerRoadMovesExistingPiersAndShortAutoGetsSupports(GameTestHelper h){
    var l=h.getLevel();var d=RoadData.get(l);
    for(int x=38000;x<38060;x++)for(int z=37970;z<=38030;z++){var p=new BlockPos(x,0,z);l.getChunkAt(p);l.setBlock(p,Blocks.STONE.defaultBlockState(),2);}
    var a=marker(h,38008,15,38000,-90);var b=marker(h,38048,15,38000,-90);
    var upper=d.connect(l,null,a,b,road(Style.O4_YELLOW,Structure.AUTO),null);
    h.assertTrue(upper.structures().stream().anyMatch(p->p.pier()&&p.height()>2),"automatic elevated road has support");
    var c=marker(h,38002,2,38000,-90);var e=marker(h,38054,2,38000,-90);
    var lower=d.connect(l,null,c,e,road(Style.O8_YELLOW,Structure.GROUND),null);var low=d.index.roads.get(lower.id());var up=d.index.roads.get(upper.id());
    var shafts=up.record.structures().stream().filter(p->p.pier()&&p.height()>2).toList();h.assertTrue(shafts.isEmpty(),"blocked automatic support is omitted");
    for(var p:shafts)h.assertTrue(!RoadQueries.contains(low.mesh,p.a(),100,.3),"all upper piers outside lower road");
    h.assertTrue(up.record.structures().stream().noneMatch(p->!p.pier()&&p.material()==Material.CONCRETE&&p.a().distance(p.b())>lower.settings().width()),"no forced crossbeam across lower road");
    d.remove(l,null,upper.id());d.remove(l,null,lower.id());
    var f=marker(h,38100,12,38100,-90);var g=marker(h,38108,12,38100,-90);var shortRoad=d.connect(l,null,f,g,road(Style.O2_YELLOW,Structure.AUTO),null);h.assertTrue(shortRoad.structures().stream().anyMatch(p->p.pier()&&p.height()>2),"eight-block auto span has pillar");d.remove(l,null,shortRoad.id());h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision32",timeoutTicks=12000)
  public static void lanePaintKeepsWorldAndOtherRoadsExact(GameTestHelper h){
    var l=h.getLevel();var d=RoadData.get(l);var a=marker(h,39000,2,39000,-90);var b=marker(h,39064,2,39000,-90);var c=marker(h,39000,2,39080,-90);var e=marker(h,39064,2,39080,-90);
    var r=d.connect(l,null,a,b,road(Style.O8_YELLOW,Structure.GROUND),null);var other=d.connect(l,null,c,e,road(Style.H8_RAIL,Structure.GROUND),null);var beforeOther=d.index.roads.get(other.id()).record.save();var before=d.index.roads.get(r.id());var blocks=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();for(long key:before.cells.keySet()){var p=BlockPos.of(key);blocks.put(p,l.getBlockState(p));}
    var t=new CompoundTag();t.putUUID("Id",r.id());t.putInt("Signature",GantryTool.signature(before.record));t.putString("Key","divider:0");t.putString("Pattern","YELLOW_SOLID");t.putDouble("Width",.2);d.editLaneLine(l,null,t);
    var after=d.index.roads.get(r.id());h.assertTrue(after.record.structures().equals(before.record.structures())&&after.mesh.samples().equals(before.mesh.samples()),"paint retains exact saved geometry");h.assertTrue(d.index.roads.get(other.id()).record.save().equals(beforeOther),"other road is byte-for-byte unchanged");blocks.forEach((p,s)->h.assertTrue(l.getBlockState(p).equals(s),"world cell unchanged "+p));
    h.assertTrue(RoadData.load(d.save(new CompoundTag())).index.roads.get(r.id()).record.settings().options().laneLines().equals(after.record.settings().options().laneLines()),"paint edit reloads");
    d.connect(l,null,a,b,road(Style.O8_YELLOW,Structure.GROUND).options(r.settings().options().extras(true,false,true)),r.id());h.assertTrue(d.index.roads.get(other.id()).record.save().equals(beforeOther),"ordinary road edit leaves independent road unchanged");d.remove(l,null,r.id());d.remove(l,null,other.id());h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision32",timeoutTicks=12000)
  public static void sidewalkCanBeDugRefilledAndOuterRingRestored(GameTestHelper h){
    var l=h.getLevel();var d=RoadData.get(l);for(int x=40000;x<=40060;x++)for(int z=39980;z<=40020;z++){var p=new BlockPos(x,1,z);l.getChunkAt(p);l.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);}
    var a=marker(h,40004,2,40000,-90);var b=marker(h,40056,2,40000,-90);var s=road(Style.O2_YELLOW,Structure.GROUND);s=s.options(s.options().sidewalk(new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks")));var r=d.connect(l,null,a,b,s,null);var built=d.index.roads.get(r.id());built.cells.size();
    BlockPos candidate=null;for(long key:built.walkTops.keySet()){var p=BlockPos.of(key);if(p.getY()==1&&built.column(p)==null&&RoadBlocks.isCollider(l.getBlockState(p))){candidate=p;break;}}h.assertTrue(candidate!=null,"partial sidewalk terrain cell exists");
    h.assertTrue(d.excavateSupport(l,candidate,null),"original terrain can be dug");h.assertTrue(RoadBlocks.canInfill(l,candidate,Blocks.DIRT.defaultBlockState()),"empty sidewalk cell accepts vanilla full block");h.assertTrue(d.infill(l,candidate,Blocks.DIRT.defaultBlockState(),null),"refill succeeds");
    int border=0;for(int x=40010;x<40050;x++)for(int z=39980;z<40020;z++)if(l.getBlockState(new BlockPos(x,1,z)).is(Blocks.STONE_BRICKS))border++;h.assertTrue(border>=70,"outer adjacent terrain converted to vanilla sidewalk block");d.remove(l,null,r.id());h.assertTrue(l.getBlockState(candidate).is(Blocks.DIRT),"player refill survives road removal");for(int x=40010;x<40050;x++)for(int z=39980;z<40020;z++)if(!new BlockPos(x,1,z).equals(candidate))h.assertTrue(l.getBlockState(new BlockPos(x,1,z)).is(Blocks.GRASS_BLOCK),"border terrain restored");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision32",timeoutTicks=12000)
  public static void yEditorBuildEditReloadDelete(GameTestHelper h){
    var l=h.getLevel();var d=RoadData.get(l);var a=marker(h,41000,2,41000,180);var b=marker(h,41028,2,40900,180);var c=marker(h,40972,2,40900,0);
    var aa=marker(h,41000,2,41040,180);var bb=marker(h,41028,2,40860,180);var cc=marker(h,40972,2,40860,0);
    var ra=d.connect(l,null,aa,a,road(Style.O8_YELLOW,Structure.GROUND),null);var rb=d.connect(l,null,b,bb,road(Style.O4_ONE,Structure.GROUND),null);var rc=d.connect(l,null,cc,c,road(Style.O4_ONE,Structure.GROUND),null);
    long[] points={a.asLong(),b.asLong(),c.asLong()};var stack=new ItemStack(SplineRoads.Y_JUNCTION.get());stack.getOrCreateTag().putLongArray("Points",points);stack.getOrCreateTag().putString("Dimension",l.dimension().location().toString());var command=YJunctionTool.payload(l,null,points,null);YJunctionTool.build(l,null,command,stack);
    UUID id=d.interchanges.entrySet().stream().filter(e->e.getValue().getBoolean("YJunction")&&Arrays.equals(points,e.getValue().getLongArray("Points"))).findFirst().orElseThrow().getKey();
    h.assertTrue(d.index.roads.values().stream().filter(r->id.equals(r.record.assembly())).count()==3,"Y has one stem and two single carriageways");h.assertTrue(RoadData.load(d.save(new CompoundTag())).interchanges.get(id).getBoolean("YJunction"),"Y descriptor survives save/reload");
    var edit=YJunctionTool.payload(l,null,points,id);edit.putDouble("Tension",.5);YJunctionTool.build(l,null,edit,stack);var part=d.index.roads.values().stream().filter(r->id.equals(r.record.assembly())).findFirst().orElseThrow();d.remove(l,null,part.record.id());h.assertTrue(!d.interchanges.containsKey(id)&&d.index.roads.values().stream().noneMatch(r->id.equals(r.record.assembly())),"delete any Y section removes its complete assembly");for(var r:List.of(ra,rb,rc)){h.assertTrue(d.index.roads.containsKey(r.id()),"external road retained");d.remove(l,null,r.id());}h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision32",timeoutTicks=12000)
  public static void tunnelConnectionsRemainOpen(GameTestHelper h){Revision28GameTests.bridgeAndTunnelConnectToOrdinaryRoad(h);}
  @GameTest(template="empty",templateNamespace="splineroads_revision32",timeoutTicks=12000)
  public static void protectedTunnelConflictStillRejects(GameTestHelper h){Revision28GameTests.overpassAvoidsLowerRoad(h);}
}
