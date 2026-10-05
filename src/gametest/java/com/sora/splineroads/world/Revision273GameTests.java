package com.sora.splineroads.world;
import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadGeometry.*;import com.sora.splineroads.core.CorridorPlanner.*;import java.util.*;import net.minecraft.core.BlockPos;import net.minecraft.gametest.framework.*;import net.minecraft.nbt.*;import net.minecraft.world.level.block.Blocks;import net.minecraftforge.gametest.*;
@GameTestHolder("splineroads_revision273") @PrefixGameTestTemplate(false)
public final class Revision273GameTests {
 @GameTest(template="empty",templateNamespace="splineroads_revision273",timeoutTicks=12000)
 public static void retireOldFrontageMarkersAtomically(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);var t=Revision272GameTests.input(h,5000,6000,600,Kind.FRONTAGE,Adjustment.BOTH);var ids=new HashSet<>(data.interchanges.keySet());Interchanges.build(level,null,t);var id=Revision272GameTests.group(data,ids);var saved=data.interchanges.get(id);
  var host=data.index.roads.values().stream().filter(r->id.equals(r.record.assembly())&&!r.mesh.settings().style().ramp()&&!RoadProfile.catalog(r.mesh.settings().style()).twoWay()).findFirst().orElseThrow();
  Set<BlockPos> old=new HashSet<>();for(long p:saved.getLongArray("PreservedNodes"))if(Arrays.stream(saved.getLongArray("Points")).noneMatch(n->n==p))old.add(BlockPos.of(p));
  // Earlier versions kept generated interior markers in PreservedNodes. Repair those too.
  var middle=RoadStructures.sample(host.mesh,90).center();var ghost=BlockPos.containing(middle.x(),middle.y(),middle.z());Revision27GameTests.marker(level,ghost);old.add(ghost);
  long[] kept=Arrays.copyOf(saved.getLongArray("PreservedNodes"),saved.getLongArray("PreservedNodes").length+1);kept[kept.length-1]=ghost.asLong();saved.putLongArray("PreservedNodes",kept);
  t=Interchanges.payload(level,null,saved.getLongArray("Points"),id);t.put("Corridor",Corridors.write(new Config(Kind.FRONTAGE,Sides.BOTH,Access.BOTH,18,2.25,Adjustment.BOTH)));
  var plan=Interchanges.plan(t);var aux=plan.legs().stream().filter(l->l.name().contains("辅路")).findFirst().orElseThrow().mesh().first().center();var blocked=BlockPos.containing(aux.x(),aux.y(),aux.z());level.setBlock(blocked,Blocks.CHEST.defaultBlockState(),2);var before=data.save(new CompoundTag());boolean rejected=false;
  try{Interchanges.build(level,null,t);}catch(IllegalArgumentException e){rejected=e.getMessage().contains("占用");}
  h.assertTrue(rejected&&before.equals(data.save(new CompoundTag())),"failed rebuild does not partly retire endpoints");for(var p:old)h.assertTrue(level.getBlockEntity(p) instanceof NodeEntity,"old endpoints survive a failed update");level.removeBlock(blocked,false);
  Interchanges.build(level,null,t);saved=data.interchanges.get(id);for(var p:old)h.assertTrue(!(level.getBlockEntity(p) instanceof NodeEntity),"obsolete endpoint removed, including legacy ghosts");
  h.assertTrue(saved.getLongArray("PreservedNodes").length==6,"only two main and four current frontage markers persisted");
  var loaded=RoadData.load(data.save(new CompoundTag()));h.assertTrue(loaded.interchanges.get(id).getLongArray("PreservedNodes").length==6,"clean marker list survives reload");
  Interchanges.build(level,null,Interchanges.payload(level,null,saved.getLongArray("Points"),id));h.assertTrue(data.interchanges.get(id).getLongArray("PreservedNodes").length==6,"repeated update does not accumulate markers");
  Interchanges.remove(level,null,id);h.assertTrue(RoadWorkChunks.heldCount(level)==0,"tickets released");h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_revision273",timeoutTicks=12000)
 public static void slightlySkewedOneWayToTwoWay(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);var t=Revision272GameTests.input(h,5000,6400,600,Kind.FRONTAGE,Adjustment.BOTH);t.put("Corridor",Corridors.write(new Config(Kind.FRONTAGE,Sides.BOTH,Access.NONE,14,2.25)));
  var ids=new HashSet<>(data.interchanges.keySet());Interchanges.build(level,null,t);UUID id=Revision272GameTests.group(data,ids);
  var hosts=data.index.roads.values().stream().filter(r->id.equals(r.record.assembly())&&!r.mesh.settings().style().ramp()&&!RoadProfile.catalog(r.mesh.settings().style()).twoWay()).toList();
  var wide=new Settings(Mode.AUTO,Style.O4_YELLOW,21,1,.4,90);
  for(var host:hosts){BlockPos center=host.record.b();V pos=host.mesh.last().center().add(host.record.end().direction().mul(96)).add(new V(0,0,4));BlockPos end=Revision272GameTests.marker(h,(int)Math.floor(pos.x()),2,(int)Math.floor(pos.z()));
   CompoundTag preview=new CompoundTag();preview.putLong("A",center.asLong());preview.putLong("B",end.asLong());preview.put("StartNode",RoadRecord.writeNode(RoadData.requireNode(level,center,null).constructionNode()));preview.put("EndNode",RoadRecord.writeNode(RoadData.requireNode(level,end,null).constructionNode()));preview.put("Settings",RoadRecord.writeSettings(wide));data.jointPayload(preview,center,end,null);AutoJunctions.enrich(preview,data,center,end,null);
   h.assertTrue(AutoJunctions.preview(preview).centers().size()==1,"skewed mixed-way preview builds a junction");var extension=data.connect(level,null,center,end,wide,null,false,false);var junction=AutoJunctions.center(data,center);h.assertTrue(junction!=null,"skewed mixed-way actual build succeeds");
   var loaded=RoadData.load(data.save(new CompoundTag()));h.assertTrue(loaded.junctions.containsKey(junction.getUUID("Id")),"new boundary topology reloads");
   h.assertTrue(loaded.index.roads.values().stream().anyMatch(r->r.record.junction()!=null&&r.record.junction().geometryVersion()==28),"new geometry version is explicit in saved pieces");data.remove(level,null,extension.id());
  }
  Interchanges.remove(level,null,id);h.assertTrue(RoadWorkChunks.heldCount(level)==0,"tickets released");h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_revision273",timeoutTicks=12000)
 public static void zeroGapLayeredCommonRoad(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);var t=Revision272GameTests.input(h,5000,6800,600,Kind.LAYERED,Adjustment.BOTH);t.put("Corridor",Corridors.write(new Config(Kind.LAYERED,Sides.BOTH,Access.BOTH,0,2.25)));
  var preview=Corridors.preview(level,null,t);var ids=new HashSet<>(data.interchanges.keySet());Interchanges.build(level,null,preview);UUID id=Revision272GameTests.group(data,ids);var saved=data.interchanges.get(id);
  int common=0;for(Tag tag:saved.getList("CorridorRoles",10)){CompoundTag role=(CompoundTag)tag;if(!role.getString("Role").contains("共通段"))continue;common++;var built=data.index.roads.get(role.getUUID("Road"));h.assertTrue(built.mesh.samples().stream().allMatch(s->Math.abs(s.halfWidth()*2-built.mesh.settings().width())<.001),"world common lane keeps full width");}
  h.assertTrue(common==2,"both common lanes constructed at zero outside gap");h.assertTrue(saved.getLongArray("PreservedNodes").length==4,"common lanes introduce no stray markers");h.assertTrue(Corridors.read(RoadData.load(data.save(new CompoundTag())).interchanges.get(id).getCompound("Corridor")).gap()==0,"zero gap persists");
  Interchanges.removeRamps(level,null,id);h.assertTrue(data.index.roads.values().stream().filter(r->r.record.a().getZ()==6800&&r.record.a().getX()>=4900).count()==2,"ramp-only deletion removes common lanes too");h.assertTrue(RoadWorkChunks.heldCount(level)==0,"tickets released");h.succeed();
 }
}
