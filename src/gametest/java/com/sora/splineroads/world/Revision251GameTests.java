package com.sora.splineroads.world;
import com.sora.splineroads.SplineRoads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;
@GameTestHolder("splineroads_revision251") @PrefixGameTestTemplate(false)
public final class Revision251GameTests {
 @GameTest(template="empty",templateNamespace="splineroads_revision251",timeoutTicks=36000)
 public static void replaceOldFiveWayWithDoubleRamps(GameTestHelper h){replace(h,5,102000);}
 @GameTest(template="empty",templateNamespace="splineroads_revision251",timeoutTicks=36000)
 public static void replaceOldSixWayWithDoubleRamps(GameTestHelper h){replace(h,6,106000);}
 static volatile String stage="starting";
 static void stage(String message){stage=message;System.out.println("MIGRATION STAGE: "+message);}
 static void replace(GameTestHelper h,int n,int origin){
  Timer monitor=new Timer(true);monitor.schedule(new TimerTask(){public void run(){
   long used=java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()/1048576;
   var trace=Thread.getAllStackTraces().entrySet().stream().filter(e->e.getKey().getName().equals("Server thread")).findFirst();
   System.out.println("MIGRATION PROGRESS "+stage+" heapMiB="+used+" stack="+(trace.isEmpty()?"none":Arrays.stream(trace.get().getValue()).limit(4).toList()));
  }},30000,30000);
  try {
  stage(n+" old plan");
  var level=h.getLevel();var data=RoadData.get(level);double[] angles=n==5?new double[]{0,180,90,270,45}:new double[]{0,180,60,240,120,300};Node[] nodes=new Node[n];
  for(int i=0;i<n;i++){double t=Math.toRadians(angles[i]);nodes[i]=new Node(new V(origin+Math.cos(t)*900,90,origin+Math.sin(t)*900),0,0);}
  Settings main=new Settings(Mode.STRAIGHT,Style.H6_RAIL,Style.H6_RAIL.defaultWidth(),1,.4,90);
  Options old=new Options(n==5?Preset.DIRECTIONAL_FIVE:Preset.DIRECTIONAL_SIX,false,1,96,20,5,2,5,true,false,0);
  Plan previous=LegacyMulti250.plan(nodes,new Settings[]{main,main,main},old,90);
  long[] points=new long[n];Set<BlockPos> selected=new HashSet<>();ListTag savedNodes=new ListTag();
  for(int i=0;i<n;i++){
   Node node=previous.anchors().get(i);BlockPos p=BlockPos.containing(node.position().x(),node.position().y(),node.position().z());
   level.getChunkAt(p);level.setBlock(p,SplineRoads.NODE.get().defaultBlockState(),3);NodeEntity marker=(NodeEntity)level.getBlockEntity(p);marker.owner=new UUID(0,0);marker.heightExplicit=true;marker.apply(node);
   points[i]=p.asLong();selected.add(p);savedNodes.add(RoadRecord.writeNode(node));
  }
  UUID id=UUID.randomUUID();CompoundTag descriptor=new CompoundTag();descriptor.putString("Kind","interchange");descriptor.putUUID("Id",id);descriptor.putUUID("Owner",new UUID(0,0));descriptor.putLongArray("Points",points);descriptor.put("Nodes",savedNodes);descriptor.putDouble("MultiBaseY",90);descriptor.put("Options",Interchanges.write(old));
  for(String key:List.of("Main1","Main2","Main3"))descriptor.put(key,RoadRecord.writeSettings(main));
  stage(n+" old road raster");
  var built=new ArrayList<RoadIndex.Built>();int part=0;
  for(Leg leg:previous.legs()){
   Mesh m=leg.mesh();Node a=RoadRibbon.start(m),b=RoadRibbon.end(m);BlockPos pa=BlockPos.containing(a.position().x(),a.position().y(),a.position().z()),pb=BlockPos.containing(b.position().x(),b.position().y(),b.position().z());
   RoadRecord r=new RoadRecord(UUID.nameUUIDFromBytes((id+":"+part++).getBytes(java.nio.charset.StandardCharsets.UTF_8)),new UUID(0,0),pa,pb,a,b,m.settings(),false,4).alignment(id,m);built.add(new RoadIndex.Built(r));
  }
  // Old sampled geometry is installed intact, so this exercises migration rather
  // than rebuilding an already compact 0.25.1 fixture twice.
  data.interchanges.put(id,descriptor.copy());
  stage(n+" old assembly transaction");
  data.replaceAssembly(level,null,built,Set.of(),selected,List.of(),RoadLimits.MAX_MULTI_INTERCHANGE_EDIT_CELLS);
  Set<Long> footprint=new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
  for(var r:data.index.roads.values())if(id.equals(r.record.assembly())){footprint.addAll(r.cells.keySet());footprint.addAll(r.clearanceCells);}
  int oldCells=footprint.size();built=null;stage(n+" old committed, cells="+oldCells);
  var command=Interchanges.payload(level,null,points,id);
  var next=new Options(old.preset(),false,2,96,20,5,2,9,true,true,64);command.put("Options",Interchanges.write(next));
  var planned=Interchanges.plan(command);
  for(Leg leg:planned.legs()){
   Mesh m=leg.mesh();Node a=RoadRibbon.start(m),b=RoadRibbon.end(m);RoadRecord r=new RoadRecord(UUID.randomUUID(),new UUID(0,0),BlockPos.containing(a.position().x(),a.position().y(),a.position().z()),BlockPos.containing(b.position().x(),b.position().y(),b.position().z()),a,b,m.settings(),false,4).alignment(id,m);
   var probe=new RoadIndex.Built(r);footprint.addAll(probe.cells.keySet());footprint.addAll(probe.clearanceCells);
  }
  int union=footprint.size();footprint=null;stage(n+" update transaction, scan union="+union);
  String result=Interchanges.build(level,null,command);System.out.println("MIGRATION "+n+" old cells="+oldCells+", old+new scan="+union+": "+result);
  var roads=data.index.roads.values().stream().filter(r->id.equals(r.record.assembly())).toList();
  h.assertTrue(roads.size()==planned.legs().size(),"entire old assembly replaced");
  h.assertTrue(roads.stream().filter(r->r.record.settings().style().ramp()).allMatch(r->r.record.settings().style()==Style.R2),"all ramps converted to two lanes");
  h.assertTrue(RoadWorkChunks.heldCount(level)==0,"all chunk tickets released");
  stage(n+" save and reload");
  var loaded=RoadData.load(data.save(new CompoundTag()));for(var r:roads)h.assertTrue(loaded.index.roads.get(r.record.id()).mesh.samples().equals(r.mesh.samples()),"full geometry survives save/reload");
  h.succeed();
  } finally {monitor.cancel();}
 }
}
