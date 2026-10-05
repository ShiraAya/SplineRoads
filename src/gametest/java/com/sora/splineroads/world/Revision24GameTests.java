package com.sora.splineroads.world;

import com.sora.splineroads.SplineRoads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.JunctionSpec.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import com.sora.splineroads.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder("splineroads_revision24") @PrefixGameTestTemplate(false)
public final class Revision24GameTests {
  static BlockPos node(GameTestHelper h,int x,int y,int z){var p=new BlockPos(x,y,z);h.getLevel().getChunkAt(p);h.getLevel().setBlock(p,SplineRoads.NODE.get().defaultBlockState(),3);((NodeEntity)h.getLevel().getBlockEntity(p)).owner=new UUID(0,0);return p;}
  static Settings settings(Style style){return new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.35,90).structure(Structure.GROUND);}
  @GameTest(template="empty",templateNamespace="splineroads_revision24",timeoutTicks=6000)
  public static void remoteJunctionCornerAndIsolatedEdit(GameTestHelper h){
    var l=h.getLevel();var d=RoadData.get(l);var c=node(h,71000,90,71000);var a=node(h,71160,90,71000);var b=node(h,71000,90,71160);var e=node(h,71160,90,71160);
    var first=d.connect(l,null,c,a,settings(Style.O4_YELLOW),null);var remote=d.connect(l,null,c,b,settings(Style.O4_YELLOW),null,false,true);
    var unchanged=d.index.roads.get(remote.id()).record.save();var remoteConfig=AutoJunctions.center(d,c).copy();
    var command=new CompoundTag();command.putLong("A",a.asLong());command.putLong("B",e.asLong());command.put("StartNode",RoadRecord.writeNode(((NodeEntity)l.getBlockEntity(a)).constructionNode()));command.put("EndNode",RoadRecord.writeNode(((NodeEntity)l.getBlockEntity(e)).constructionNode()));command.put("Settings",RoadRecord.writeSettings(settings(Style.O4_YELLOW)));d.jointPayload(command,a,e,null);AutoJunctions.enrich(command,d,a,e,null);
    h.assertTrue(command.getBoolean("AutoJunction")&&command.getBoolean("ForceJunction"),"a distant junction does not hide the local angled second-road choice");
    var preview=AutoJunctions.preview(command);h.assertTrue(!preview.centers().isEmpty(),"corner preview contains an actual junction");
    var next=d.connect(l,null,a,e,settings(Style.O4_YELLOW),null,false,command.getBoolean("ForceJunction"));
    h.assertTrue(AutoJunctions.center(d,a)!=null,"the previewed corner can actually be constructed");
    h.assertTrue(remoteConfig.equals(AutoJunctions.center(d,c)),"remote junction configuration is unchanged");
    d.connect(l,null,a,e,settings(Style.O6_RAIL),next.id());
    h.assertTrue(unchanged.equals(d.index.roads.get(remote.id()).record.save()),"editing the new road never rewrites the remote branch");
    h.assertTrue(remoteConfig.equals(AutoJunctions.center(d,c)),"remote phases, lane mappings and cross-section persist through edit");
    var payload=AutoJunctions.payload(l,null,AutoJunctions.center(d,a).getUUID("Id"));var spec=JunctionCodec.read(payload.getCompound("Spec"));var arms=new ArrayList<Arm>();
    for(Arm arm:spec.arms()){var lanes=new ArrayList<>(arm.lanes());for(int i=0;i<lanes.size();i++)lanes.set(i,lanes.get(i).signals(true,-1));arms.add(arm.lanes(lanes));}
    payload.put("Spec",JunctionCodec.write(spec.arms(arms)));payload.getCompound("Spec").putString("Control","SIGNALS");AutoJunctions.edit(l,null,payload);
    h.assertTrue(d.index.roads.values().stream().flatMap(r->r.signalHeads.stream()).anyMatch(p->p.material()==Material.SIGNAL_LEFT),"corner builds an eligible independent left head");
    var loaded=RoadData.load(d.save(new CompoundTag()));
    for(var road:d.index.roads.values())if(road.record.junction()!=null){
      var copy=loaded.index.roads.get(road.record.id());h.assertTrue(copy.mesh.samples().equals(road.mesh.samples()),"new junction geometry survives save/load");
      for(long time:new long[]{0,400,2000,6000})h.assertTrue(road.signalFaces(time).equals(copy.signalFaces(time)),"all lamp bindings survive packed structure and spec round trip");
    }
    d.remove(l,null,next.id());d.remove(l,null,remote.id());d.remove(l,null,first.id());h.assertTrue(RoadWorkChunks.heldCount(l)==0,"corner edit releases work chunks");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision24",timeoutTicks=2400)
  public static void preserveNeighborFarTransition(GameTestHelper h){
    var l=h.getLevel();var d=RoadData.get(l);var a=node(h,73000,90,73000);var b=node(h,73120,90,73000);var c=node(h,73240,90,73000);var e=node(h,73360,90,73000);
    var first=d.connect(l,null,a,b,settings(Style.O2_YELLOW),null);var middle=d.connect(l,null,b,c,settings(Style.O4_YELLOW),null);var last=d.connect(l,null,c,e,settings(Style.O6_RAIL),null);
    var far=d.index.roads.get(middle.id()).record.settings().options().ends().end();var tail=d.index.roads.get(last.id()).record.save();
    d.connect(l,null,a,b,settings(Style.O4_YELLOW),first.id());
    h.assertTrue(Objects.equals(far,d.index.roads.get(middle.id()).record.settings().options().ends().end()),"editing AB keeps BC's C transition intact");
    h.assertTrue(tail.equals(d.index.roads.get(last.id()).record.save()),"CD remains byte-for-byte unchanged");
    d.remove(l,null,first.id());d.remove(l,null,middle.id());d.remove(l,null,last.id());h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision24",timeoutTicks=2400)
  public static void groundAutoRailNoFloatingFragments(GameTestHelper h){
    var l=h.getLevel();var d=RoadData.get(l);
    for(int x=74000;x<=74072;x++)for(int z=73986;z<=74014;z++)l.setBlock(new BlockPos(x,89,z),Blocks.STONE.defaultBlockState(),2);
    var a=node(h,74004,90,74000);var b=node(h,74068,90,74000);
    ((NodeEntity)l.getBlockEntity(a)).offsetY=.0625;((NodeEntity)l.getBlockEntity(a)).heightExplicit=true;
    ((NodeEntity)l.getBlockEntity(b)).offsetY=.0625;((NodeEntity)l.getBlockEntity(b)).heightExplicit=true;
    Settings s=settings(Style.O4_YELLOW).structure(Structure.AUTO);
    var road=d.connect(l,null,a,b,s,null);
    for(int edit=0;edit<2;edit++){
      h.assertTrue(d.index.roads.get(road.id()).record.structures().stream().noneMatch(p->p.material()==Material.CONCRETE&&p.width()==.42),"1/16-block surface lift creates no spurious bridge rail");
      d.connect(l,null,a,b,s,road.id());
    }
    d.remove(l,null,road.id());h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision24",timeoutTicks=12000)
  public static void fiveWayLifecycle(GameTestHelper h){multi(h,5,80000);}
  @GameTest(template="empty",templateNamespace="splineroads_revision24",timeoutTicks=12000)
  public static void sixWayLifecycle(GameTestHelper h){multi(h,6,85000);}
  static void multi(GameTestHelper h,int count,int origin){
    var l=h.getLevel();var d=RoadData.get(l);double[] angles=count==5?new double[]{0,180,90,270,45}:new double[]{0,180,60,240,120,300};
    long[] points=new long[count];for(int i=0;i<count;i++){double angle=Math.toRadians(angles[i]);points[i]=node(h,origin+(int)Math.round(Math.cos(angle)*640),90,origin+(int)Math.round(Math.sin(angle)*640)).asLong();}
    var payload=Interchanges.payload(l,null,points,null);for(String key:List.of("Main1","Main2","Main3"))payload.put(key,RoadRecord.writeSettings(settings(Style.O4_YELLOW)));
    payload.put("Options",Interchanges.write(new Options(count==5?Preset.DIRECTIONAL_FIVE:Preset.DIRECTIONAL_SIX,false,1,80,16,4,2,0,true,false,0)));
    var preview=Interchanges.plan(payload);h.assertTrue(preview.legs().size()==(count==5?19:27),"preview has all physical legs");
    Interchanges.build(l,null,payload);
    var descriptor=d.interchanges.values().stream().filter(t->t.getLongArray("Points").length==count).findFirst().orElseThrow();UUID id=descriptor.getUUID("Id");
    var built=d.index.roads.values().stream().filter(r->id.equals(r.record.assembly())).toList();h.assertTrue(built.size()==preview.legs().size(),"complete preview builds atomically");
    var loaded=RoadData.load(d.save(new CompoundTag()));for(var road:built)h.assertTrue(loaded.index.roads.get(road.record.id()).mesh.samples().equals(road.mesh.samples()),"all interchange alignments survive reload");
    long[] fitted=descriptor.getLongArray("Points");var edit=Interchanges.payload(l,null,fitted,id);var originalNodes=edit.getList("Nodes",Tag.TAG_COMPOUND).copy();Interchanges.build(l,null,edit);
    h.assertTrue(d.interchanges.get(id).getList("Nodes",Tag.TAG_COMPOUND).equals(originalNodes),"ordinary reopen/save does not repeatedly raise endpoints");
    UUID ramp=d.index.roads.values().stream().filter(r->id.equals(r.record.assembly())&&r.record.settings().style().ramp()).findFirst().orElseThrow().record.id();Interchanges.removePart(l,null,ramp);h.assertTrue(d.interchanges.get(id).getBoolean("Partial"),"individual ramp deletion keeps editable group metadata");
    edit=Interchanges.payload(l,null,fitted,id);Interchanges.build(l,null,edit);h.assertTrue(!d.interchanges.get(id).getBoolean("Partial"),"whole edit restores missing movement");
    Interchanges.removeRamps(l,null,id);h.assertTrue(!d.interchanges.containsKey(id),"ramp-only removal detaches generated assembly");
    var mains=d.index.roads.values().stream().filter(r->Arrays.stream(fitted).anyMatch(v->r.record.a().asLong()==v||r.record.b().asLong()==v)).toList();
    h.assertTrue(mains.size()==(count==5?2:3),"only actual through mainlines remain; five-way E stub is removed");
    for(long p:fitted)h.assertTrue(l.getBlockEntity(BlockPos.of(p)) instanceof NodeEntity,"selected endpoint remains after ramp removal");
    for(var main:mains)d.remove(l,null,main.record.id());h.assertTrue(RoadWorkChunks.heldCount(l)==0,"large interchange lifecycle releases work chunks");h.succeed();
  }
}
