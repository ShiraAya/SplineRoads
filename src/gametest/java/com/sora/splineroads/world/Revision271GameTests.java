package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.CorridorPlanner.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;
@GameTestHolder("splineroads_revision271") @PrefixGameTestTemplate(false)
public final class Revision271GameTests {
  static Settings style(Style s){return new Settings(Mode.AUTO,s,s.defaultWidth(),1,.4,90);}
  static BlockPos marker(GameTestHelper h,int x,int y,int z){BlockPos p=new BlockPos(x,y,z);Revision27GameTests.marker(h.getLevel(),p);return p;}
  static CompoundTag corridor(GameTestHelper h,int z){var t=Revision27GameTests.inputs(h,Kind.FRONTAGE,z);t.put("Corridor",Corridors.write(new Config(Kind.FRONTAGE,Sides.BOTH,Access.NONE,18,2.25)));Interchanges.build(h.getLevel(),null,t);return t;}
  @GameTest(template="empty",templateNamespace="splineroads_revision271",timeoutTicks=12000)
  public static void fixedFrontageWidthTransition(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var t=corridor(h,2000);UUID group=Revision27GameTests.group(data,t);
    var host=data.index.roads.values().stream().filter(r->group.equals(r.record.assembly())&&!RoadProfile.catalog(r.mesh.settings().style()).twoWay()).findFirst().orElseThrow();
    var old=host.record.save();V p=host.mesh.last().center().add(host.record.end().direction().mul(128));BlockPos end=marker(h,(int)Math.floor(p.x()),2,(int)Math.floor(p.z()));
    var road=data.connect(level,null,host.record.b(),end,style(Style.O2_ONE),null,false);
    Mesh live=data.index.roads.get(road.id()).mesh;
    h.assertTrue(Math.abs(live.first().halfWidth()-host.mesh.last().halfWidth())<1e-7,"fixed frontage seam matches exactly");
    h.assertTrue(Math.abs(live.last().halfWidth()*2-Style.O2_ONE.defaultWidth())<.001,"new road smoothly returns to its own width");
    var after=data.index.roads.get(host.record.id());
    h.assertTrue(host.mesh.samples().equals(after.mesh.samples())&&host.record.settings().equals(after.record.settings())&&group.equals(after.record.assembly()),"generated host geometry and section remain unchanged");
    var loaded=RoadData.load(data.save(new CompoundTag()));h.assertTrue(Math.abs(loaded.index.roads.get(road.id()).mesh.first().halfWidth()-host.mesh.last().halfWidth())<1e-7,"transition survives reload");
    data.remove(level,null,road.id());Interchanges.remove(level,null,group);h.assertTrue(RoadWorkChunks.heldCount(level)==0,"no tickets leaked");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision271",timeoutTicks=12000)
  public static void mixedDirectionFrontageJunction(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var t=corridor(h,2200);UUID group=Revision27GameTests.group(data,t);
    var host=data.index.roads.values().stream().filter(r->group.equals(r.record.assembly())&&!RoadProfile.catalog(r.mesh.settings().style()).twoWay()).findFirst().orElseThrow();
    BlockPos center=host.record.b();V p=host.mesh.last().center().add(host.record.end().direction().mul(128));BlockPos end=marker(h,(int)Math.floor(p.x()),2,(int)Math.floor(p.z()));
    // Exactly the photographed failure: one-way frontage meets a two-way road, without forcing a junction.
    var preview=new CompoundTag();preview.putLong("A",center.asLong());preview.putLong("B",end.asLong());
    preview.put("StartNode",RoadRecord.writeNode(RoadData.requireNode(level,center,null).constructionNode()));preview.put("EndNode",RoadRecord.writeNode(RoadData.requireNode(level,end,null).constructionNode()));preview.put("Settings",RoadRecord.writeSettings(style(Style.O4_YELLOW)));
    data.jointPayload(preview,center,end,null);AutoJunctions.enrich(preview,data,center,end,null);
    h.assertTrue(AutoJunctions.requiresJunction(preview,style(Style.O4_YELLOW)),"client recognizes mixed one/two-way junction before joining widths");
    h.assertTrue(AutoJunctions.preview(preview).centers().size()==1,"preview produces the same local junction as construction");
    var road=data.connect(level,null,center,end,style(Style.O4_YELLOW),null,false,false);
    var junction=AutoJunctions.center(data,center);h.assertTrue(junction!=null,"one-way + two-way automatically becomes a junction");
    var spec=JunctionCodec.read(junction.getCompound("Spec"));
    h.assertTrue(spec.arms().stream().anyMatch(a->a.incoming()==0||a.outgoing()==0),"one-way direction preserved");
    h.assertTrue(spec.arms().stream().anyMatch(a->a.incoming()==2&&a.outgoing()==2),"two-way four-lane approach preserved");
    h.assertTrue(group.equals(data.index.roads.get(host.record.id()).record.assembly()),"trimmed frontage retains combination identity");
    BlockPos third=marker(h,center.getX(),2,center.getZ()+(center.getZ()>2200?128:-128));
    var branch=data.connect(level,null,center,third,style(Style.O2_YELLOW),null,false,false);
    h.assertTrue(JunctionCodec.read(AutoJunctions.center(data,center).getCompound("Spec")).arms().size()==3,"third direction rebuilds a T junction");
    var saved=data.save(new CompoundTag());var loaded=RoadData.load(saved);h.assertTrue(AutoJunctions.center(loaded,center)!=null&&loaded.streets.get(host.record.id()).assembly().equals(group),"junction and original frontage survive reload");
    boolean centerBlocked=false;try{AutoJunctions.removeCenter(level,null,AutoJunctions.center(data,center).getUUID("Id"));}catch(IllegalArgumentException expected){centerBlocked=expected.getMessage().contains("外接支路");}
    h.assertTrue(centerBlocked&&saved.equals(data.save(new CompoundTag())),"deleting junction cannot silently delete a combination road");
    boolean blocked=false;try{Interchanges.remove(level,null,group);}catch(IllegalArgumentException expected){blocked=expected.getMessage().contains("路口");}
    h.assertTrue(blocked&&saved.equals(data.save(new CompoundTag())),"connected combination removal is atomic");
    data.remove(level,null,branch.id());data.remove(level,null,road.id());
    h.assertTrue(AutoJunctions.center(data,center)==null,"last external branch removal removes junction");
    h.assertTrue(!data.streets.containsKey(host.record.id()),"untrimmed frontage returns to ordinary combination editing");
    h.assertTrue(data.index.roads.get(host.record.id()).mesh.last().center().distance(host.mesh.last().center())<1e-7,"original frontage length restored");
    Interchanges.remove(level,null,group);h.assertTrue(RoadWorkChunks.heldCount(level)==0,"no tickets leaked");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision271",timeoutTicks=12000)
  public static void automaticallyMoveLayeredEndpoints(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);var a=marker(h,1600,10,2500);var b=marker(h,1680,10,2500);var c=marker(h,1600,2,2500);var d=marker(h,1680,2,2500);
    var t=Corridors.initialize(Interchanges.payload(level,null,new long[]{a.asLong(),b.asLong(),c.asLong(),d.asLong()},null),Kind.LAYERED);
    t.put("Main1",RoadRecord.writeSettings(style(Style.O2_YELLOW)));t.put("Main2",RoadRecord.writeSettings(style(Style.O2_YELLOW)));
    t.put("Options",Interchanges.write(new InterchangePlanner.Options(InterchangePlanner.Preset.CLOVERLEAF,false,1,24,12,5,1).adjust(true)));
    t.put("Corridor",Corridors.write(Corridors.read(t.getCompound("Corridor")).adjustment(Adjustment.BOTH)));
    var plan=Interchanges.plan(t);h.assertTrue(plan.anchors().get(0).position().x()<1600,"short selection extends automatically");
    BlockPos outside=marker(h,1472,10,2500);var extra=data.connect(level,null,outside,a,style(Style.O2_YELLOW),null,false,false);
    var snapshot=data.save(new CompoundTag());boolean blocked=false;String reason="no rejection";
    try{Interchanges.build(level,null,t);}catch(IllegalArgumentException expected){reason=expected.getMessage();blocked=reason.contains("选区外道路");}
    h.assertTrue(blocked&&snapshot.equals(data.save(new CompoundTag())),"fitting cannot move externally connected endpoints: "+reason);
    data.remove(level,null,extra.id());level.removeBlock(outside,false);
    Set<UUID> before=new HashSet<>(data.interchanges.keySet());Interchanges.build(level,null,t);UUID group=data.interchanges.keySet().stream().filter(id->!before.contains(id)).findFirst().orElseThrow();
    var saved=data.interchanges.get(group);h.assertTrue(!Arrays.equals(t.getLongArray("Points"),saved.getLongArray("Points")),"fitted endpoints saved");
    for(int i=0;i<4;i++){var pos=BlockPos.of(saved.getLongArray("Points")[i]);var node=RoadData.requireNode(level,pos,null);h.assertTrue(node.constructionNode().position().distance(plan.anchors().get(i).position())<1e-7,"real marker moved to preview target");}
    for(long old:t.getLongArray("Points"))h.assertTrue(!(level.getBlockEntity(BlockPos.of(old)) instanceof NodeEntity),"old marker removed from road interior");
    Interchanges.remove(level,null,group);h.assertTrue(RoadWorkChunks.heldCount(level)==0,"no tickets leaked");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision271",timeoutTicks=6000)
  public static void ordinaryCornerAndRemoteJunctionRegression(GameTestHelper h){Revision24GameTests.remoteJunctionCornerAndIsolatedEdit(h);}
  @GameTest(template="empty",templateNamespace="splineroads_revision271",timeoutTicks=2400)
  public static void ordinaryFarTransitionRegression(GameTestHelper h){Revision24GameTests.preserveNeighborFarTransition(h);}
}
