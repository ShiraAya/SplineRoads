package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;
@GameTestHolder("splineroads_junction21") @PrefixGameTestTemplate(false)
public final class AutoJunction21GameTests {
 private static BlockPos node(GameTestHelper h,int x,int z){BlockPos p=new BlockPos(x,90,z);h.getLevel().setBlock(p,SplineRoads.NODE.get().defaultBlockState(),3);((NodeEntity)h.getLevel().getBlockEntity(p)).owner=new UUID(0,0);return p;}
 private static Settings s(Style style){return new Settings(Mode.AUTO,style,style.defaultWidth(),1,.35,90);}
 private static long count(RoadData data,BlockPos p){return data.index.roads.values().stream().filter(r->r.record.junction()!=null&&r.record.b().equals(p)).map(r->r.record.junction().spec().arms().size()).findFirst().orElse(0);}
 @GameTest(template="empty",templateNamespace="splineroads_junction21",timeoutTicks=1200)
 public static void sharedNodeBuildGrowEditShrinkAndRollback(GameTestHelper h){var l=h.getLevel();var d=RoadData.get(l);BlockPos c=node(h,48000,48000);int[][] xyz={{140,0},{-140,0},{0,140},{0,-140},{105,105},{-105,-105}};var roads=new ArrayList<UUID>();
  for(int i=0;i<xyz.length;i++){BlockPos end=node(h,c.getX()+xyz[i][0],c.getZ()+xyz[i][1]);Style style=i<2?Style.O2_YELLOW:i%2==0?Style.O4_GREEN:Style.O6_RAIL;var r=d.connect(l,null,c,end,s(style),null,false,false);roads.add(r.id());if(i>=2)h.assertTrue(count(d,c)==i+1,"automatically rebuilds "+(i+1)+" arms");}
  var first=d.index.roads.values().stream().filter(r->r.record.junction()!=null&&r.record.b().equals(c)).findFirst().orElseThrow();h.assertTrue(first.record.junction().spec().arms().stream().map(JunctionSpec.Arm::phase).distinct().count()==6,"new branches get independent default signal phases");var edit=Junctions.payload(l,null,new long[0],first.record.assembly());edit.getCompound("Spec").putString("Control","SIGNALS");Junctions.build(l,null,edit);
  h.assertTrue(d.index.roads.values().stream().anyMatch(r->r.record.junction()!=null&&r.record.junction().spec().control()==JunctionSpec.Control.SIGNALS),"settings applied");
  var copy=RoadData.load(d.save(new CompoundTag()));h.assertTrue(AutoJunctions.degree(copy,c,null)==6,"logical streets survive reload");for(var r:copy.index.roads.values())h.assertTrue(r.mesh.equals(RoadRecord.load(r.record.save()).mesh()),"geometry survives reload");
  BlockPos seventh=node(h,48105,47895);int before=d.index.roads.size();boolean failed=false;try{d.connect(l,null,c,seventh,s(Style.O2_YELLOW),null);}catch(IllegalArgumentException e){failed=true;}h.assertTrue(failed&&d.index.roads.size()==before,"seventh direction refused atomically");
  for(int i=5;i>=1;i--){d.remove(l,null,roads.get(i));h.assertTrue(count(d,c)==(i>=2?i:0),"deletion rebuilds remaining approaches");}
  h.assertTrue(d.index.roads.containsKey(roads.get(0)),"last ordinary street restored");d.remove(l,null,roads.get(0));h.assertTrue(!d.linked(c),"all roads removable");h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_junction21",timeoutTicks=1200)
 public static void secondRoadChoiceAndEightRingPorts(GameTestHelper h){var l=h.getLevel();var d=RoadData.get(l);BlockPos c=node(h,49000,49000),a=node(h,49100,49000),b=node(h,49000,49100);var first=d.connect(l,null,c,a,s(Style.O2_YELLOW),null);var second=d.connect(l,null,c,b,s(Style.O4_GREEN),null,false,true);h.assertTrue(count(d,c)==2,"second road can create angled two-arm junction");d.remove(l,null,second.id());d.remove(l,null,first.id());
  var oldRing=AutoJunctions.center(d,new BlockPos(49500,90,49500));if(oldRing!=null)AutoJunctions.removeCenter(l,null,oldRing.getUUID("Id"));
  BlockPos center=node(h,49500,49500);CompoundTag request=new CompoundTag();request.put("Node",RoadRecord.writeNode(((NodeEntity)l.getBlockEntity(center)).constructionNode()));request.putDouble("Radius",36);request.putInt("Lanes",2);var spec=RoundaboutTool.specification(request);/* Far-away GameTest chunks use normal, seed-dependent terrain. Prepare empty port targets before testing placement. */for(var port:RoundaboutTool.ports(spec))l.setBlock(BlockPos.containing(port.position().x(),90,port.position().z()),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);AutoJunctions.createRing(l,null,center,spec);var ports=RoundaboutTool.ports(spec);var roads=new ArrayList<UUID>();h.assertTrue(count(d,center)==0,"standalone ring before roads");
  for(int i=0;i<8;i++){var n=ports.get(i);BlockPos port=BlockPos.containing(n.position().x(),n.position().y(),n.position().z());h.assertTrue(l.getBlockEntity(port) instanceof NodeEntity,"physical ring port "+i);V far=n.position().add(n.position().sub(spec.center()).horizontalUnit().mul(90));BlockPos end=node(h,(int)Math.floor(far.x()),(int)Math.floor(far.z()));var r=d.connect(l,null,port,end,s(Style.O2_YELLOW),null);roads.add(r.id());h.assertTrue(count(d,center)==i+1,"ring entry opened "+i);}
  var copy=RoadData.load(d.save(new CompoundTag()));h.assertTrue(AutoJunctions.ringAt(copy,BlockPos.containing(ports.get(0).position().x(),90,ports.get(0).position().z()))!=null,"ring ports persist");
  for(UUID id:roads)d.remove(l,null,id);h.assertTrue(count(d,center)==0,"unused exits close after removal");var ring=AutoJunctions.center(d,center);AutoJunctions.removeCenter(l,null,ring.getUUID("Id"));for(var port:ports)h.assertTrue(!(l.getBlockEntity(BlockPos.containing(port.position().x(),90,port.position().z())) instanceof NodeEntity),"generated port removed");AutoJunctions.createRing(l,null,center,spec);AutoJunctions.removeCenter(l,null,AutoJunctions.center(d,center).getUUID("Id"));h.assertTrue(RoadWorkChunks.heldCount(l)==0,"work chunks released");h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_junction21",timeoutTicks=1200)
 public static void adjacentCentersKeepFarConnections(GameTestHelper h){var l=h.getLevel();var d=RoadData.get(l);BlockPos a=node(h,50500,50500),b=node(h,50680,50500);
  d.connect(l,null,a,node(h,50500,50600),s(Style.O2_YELLOW),null);d.connect(l,null,node(h,50500,50400),a,s(Style.O2_YELLOW),null);var bridge=d.connect(l,null,a,b,s(Style.O2_YELLOW),null);
  h.assertTrue(count(d,a)==3,"first center built");d.connect(l,null,b,node(h,50780,50500),s(Style.O2_YELLOW),null);h.assertTrue(count(d,a)==3,"continuation preserves far center");
  d.connect(l,null,b,node(h,50680,50600),s(Style.O2_YELLOW),null);h.assertTrue(count(d,a)==3&&count(d,b)==3,"both centers coexist");
  var changed=d.connect(l,null,a,b,s(Style.O4_GREEN),bridge.id());h.assertTrue(count(d,a)==3&&count(d,b)==3,"width edit rebuilds both centers");
  h.assertTrue(changed.mesh().length()<180,"bridge is trimmed at both ends");
  var first=d.index.roads.values().stream().filter(r->r.record.junction()!=null&&r.record.b().equals(a)).findFirst().orElseThrow();var payload=Junctions.payload(l,null,new long[0],first.record.assembly());payload.getCompound("Spec").putBoolean("Guides",false);payload.getCompound("Spec").putDouble("Corner",24);var preview=AutoJunctions.previewEdit(payload);Junctions.build(l,null,payload);for(var planned:preview.roads())if(planned.junction()!=null)h.assertTrue(d.index.roads.get(planned.id()).mesh.equals(planned.mesh()),"edited preview equals actual junction geometry");h.assertTrue(count(d,b)==3,"local settings edit preserves neighbor");
  var loaded=RoadData.load(d.save(new CompoundTag()));h.assertTrue(count(loaded,a)==3&&count(loaded,b)==3,"neighbor topology survives save");
  for(UUID id:new ArrayList<>(AutoJunctions.logical(d).keySet()))if(d.index.roads.containsKey(id))d.remove(l,null,id);h.succeed();
 }

}
