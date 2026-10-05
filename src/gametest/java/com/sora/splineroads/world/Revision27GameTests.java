package com.sora.splineroads.world;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.CorridorPlanner.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;

@GameTestHolder("splineroads_revision27") @PrefixGameTestTemplate(false)
public final class Revision27GameTests {
  static void marker(ServerLevel level,BlockPos p){level.getChunkAt(p);level.setBlock(p,SplineRoads.NODE.get().defaultBlockState(),2);var node=(NodeEntity)level.getBlockEntity(p);node.owner=new UUID(0,0);node.heightExplicit=true;node.apply(new Node(new V(p.getX()+.5,p.getY()+.25,p.getZ()+.5),-90,0));}
  static CompoundTag inputs(GameTestHelper h,Kind kind,int z){
    var level=h.getLevel();int length=kind==Kind.FRONTAGE?256:600;
    BlockPos a=new BlockPos(1200,kind==Kind.FRONTAGE?2:10,z),b=a.offset(length,0,0),c=new BlockPos(1200,2,z),d=c.offset(length,0,0);
    long[] points=kind==Kind.FRONTAGE?new long[]{a.asLong(),b.asLong()}:new long[]{a.asLong(),b.asLong(),c.asLong(),d.asLong()};
    for(long p:points)marker(level,BlockPos.of(p));
    CompoundTag t=Corridors.initialize(Interchanges.payload(level,null,points,null),kind);
    for(int axis=1;axis<=2;axis++){Style style=axis==2&&kind==Kind.FRONTAGE?Style.O1_ONE:Style.O2_YELLOW;t.put("Main"+axis,RoadRecord.writeSettings(new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.4,90)));}
    t.put("Corridor",Corridors.write(new Config(kind,Sides.BOTH,Access.BOTH,12,2.25)));
    t.put("Options",Interchanges.write(new InterchangePlanner.Options(InterchangePlanner.Preset.CLOVERLEAF,false,1,24,12,5,1)));return t;
  }
  static UUID group(RoadData data,CompoundTag command){return data.interchanges.entrySet().stream().filter(e->Arrays.equals(e.getValue().getLongArray("Points"),command.getLongArray("Points"))).findFirst().orElseThrow().getKey();}
  @GameTest(template="empty",templateNamespace="splineroads_revision27",timeoutTicks=12000)
  public static void frontageBuildUpdateReloadAndRemoveRamps(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);CompoundTag t=inputs(h,Kind.FRONTAGE,1200);
    Interchanges.build(level,null,t);UUID id=group(data,t);var saved=data.interchanges.get(id);
    h.assertTrue(saved.getList("CorridorRoles",10).size()==9,"main, two frontage roads, four connectors and two common segments persisted");
    long markers=Arrays.stream(saved.getLongArray("PreservedNodes")).filter(p->level.getBlockEntity(BlockPos.of(p)) instanceof NodeEntity).count();
    h.assertTrue(markers==6,"frontage ends have four real selectable markers");
    var loaded=RoadData.load(data.save(new CompoundTag()));h.assertTrue(loaded.interchanges.get(id).getCompound("Corridor").equals(saved.getCompound("Corridor")),"relationship survives world save/load");
    t=Interchanges.payload(level,null,saved.getLongArray("Points"),id);t.put("Corridor",Corridors.write(new Config(Kind.FRONTAGE,Sides.BOTH,Access.ENTRY,12,2.25)));
    Interchanges.build(level,null,t);h.assertTrue(data.index.roads.values().stream().filter(r->id.equals(r.record.assembly())&&r.mesh.settings().style().ramp()).count()==2,"editing to entry-only removes both exits");
    var frontage=data.index.roads.values().stream().filter(r->id.equals(r.record.assembly())&&!r.mesh.settings().style().ramp()&&!RoadProfile.catalog(r.mesh.settings().style()).twoWay()).findFirst().orElseThrow();
    Node a=frontage.record.end();V b=a.position().add(a.direction().mul(40));BlockPos endpoint=BlockPos.containing(b.x(),b.y(),b.z());marker(level,endpoint);
    Node end=((NodeEntity)level.getBlockEntity(endpoint)).constructionNode();
    Mesh extension=RoadGeometry.build(a,end,frontage.mesh.settings());
    RoadRecord extra=new RoadRecord(UUID.randomUUID(),new UUID(0,0),frontage.record.b(),endpoint,a,end,extension.settings(),false,4).alignment(null,extension);
    data.replaceAssembly(level,null,List.of(new RoadIndex.Built(extra)),Set.of(),Set.of(frontage.record.b(),endpoint),List.of());
    CompoundTag before=data.save(new CompoundTag());var moved=t.copy();moved.put("Corridor",Corridors.write(new Config(Kind.FRONTAGE,Sides.BOTH,Access.ENTRY,18,2.25)));boolean rejected=false;
    try{Interchanges.build(level,null,moved);}catch(IllegalArgumentException e){rejected=e.getMessage().contains("辅路端点已连接");}
    h.assertTrue(rejected&&before.equals(data.save(new CompoundTag())),"moving an externally connected frontage endpoint rejects atomically");
    Interchanges.removeRamps(level,null,id);h.assertTrue(!data.interchanges.containsKey(id),"group detached after ramp removal");
    long remaining=data.index.roads.values().stream().filter(r->r.mesh.first().center().z()>1170&&r.mesh.first().center().z()<1230&&!r.mesh.settings().style().ramp()).count();
    h.assertTrue(remaining==4,"main, both one-way frontage roads and external continuation survive ramp-only deletion");
    h.assertTrue(RoadWorkChunks.heldCount(level)==0,"work chunks released");h.succeed();
  }
  @GameTest(template="empty",templateNamespace="splineroads_revision27",timeoutTicks=12000)
  public static void layeredBuildAndWholeRemoval(GameTestHelper h){
    var level=h.getLevel();var data=RoadData.get(level);CompoundTag t=inputs(h,Kind.LAYERED,1400);
    Interchanges.build(level,null,t);UUID id=group(data,t);
    h.assertTrue(data.index.roads.values().stream().filter(r->id.equals(r.record.assembly())).count()==8,"two decks, four same-side ramps and two common segments constructed");
    var restored=RoadData.load(data.save(new CompoundTag()));h.assertTrue(Interchanges.plan(restored.interchanges.get(id)).movements()==4,"stacked roads regenerate after save/load");
    Interchanges.remove(level,null,id);h.assertTrue(data.index.roads.values().stream().noneMatch(r->id.equals(r.record.assembly())),"whole combination removal leaves no generated roads");
    for(long p:t.getLongArray("Points"))h.assertTrue(level.getBlockEntity(BlockPos.of(p)) instanceof NodeEntity,"original endpoints preserved");
    h.assertTrue(RoadWorkChunks.heldCount(level)==0,"work chunks released");h.succeed();
  }
}
