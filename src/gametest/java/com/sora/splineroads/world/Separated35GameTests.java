package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.JunctionSpec.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_separated35") @PrefixGameTestTemplate(false)
public final class Separated35GameTests {
 @GameTest(template="empty",templateNamespace="splineroads_separated35",timeoutTicks=12000)
 public static void separatedThreeAndFiveMouthBuildEditReload(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);
  for(boolean cross:new boolean[]{false,true}){
   int x=cross?73000:71000,z=x;var center=Revision32GameTests.marker(h,x,2,z,0);var a=Revision32GameTests.marker(h,x,2,z+150,180);var b=Revision32GameTests.marker(h,x+22,2,z-150,180);var c=Revision32GameTests.marker(h,x-22,2,z-150,0);
   var aa=Revision32GameTests.marker(h,x,2,z+190,180);var bb=Revision32GameTests.marker(h,x+22,2,z-190,180);var cc=Revision32GameTests.marker(h,x-22,2,z-190,0);
   var external=new ArrayList<RoadRecord>();external.add(d.connect(l,null,aa,a,Revision34GameTests.road(Style.O6_YELLOW),null));external.add(d.connect(l,null,b,bb,Revision34GameTests.road(Style.O1_ONE),null));external.add(d.connect(l,null,cc,c,Revision34GameTests.road(Style.O2_ONE),null));
   var pts=new ArrayList<>(List.of(center.asLong(),a.asLong(),b.asLong(),c.asLong()));
   if(cross)for(int side:new int[]{-1,1}){var p=Revision32GameTests.marker(h,x+side*150,2,z,side<0?-90:90);var far=Revision32GameTests.marker(h,x+side*190,2,z,side<0?-90:90);external.add(d.connect(l,null,far,p,Revision34GameTests.road(Style.O4_YELLOW),null));pts.add(p.asLong());}
   long[] points=pts.stream().mapToLong(Long::longValue).toArray();var input=Junctions.payload(l,null,points,null);var spec=JunctionCodec.read(input.getCompound("Spec"));
   h.assertTrue(spec.arms().get(0).incoming()==3&&spec.arms().get(1).outgoing()==1&&spec.arms().get(2).incoming()==2,"attached lane counts inherited");h.assertTrue(JunctionPlanner.separatedEntrances(spec),"real tool payload pairs B C");
   input.getCompound("Spec").putString("Control","SIGNALS");Junctions.build(l,null,input);
   UUID id=d.junctions.entrySet().stream().filter(e->Arrays.equals(points,e.getValue().getLongArray("Points"))).findFirst().orElseThrow().getKey();
   for(int pass=0;pass<2;pass++){
    var built=d.index.roads.values().stream().filter(r->id.equals(r.record.assembly())).toList();h.assertTrue(built.stream().filter(r->r.record.junction().get().arm()>=0).count()==points.length-1,"all selected mouths built");
    for(var r:built){h.assertTrue(r.record.junction().geometryVersion()==35,"new junction geometry version");var loaded=RoadData.load(d.save(new CompoundTag())).index.roads.get(r.record.id());h.assertTrue(loaded.mesh.samples().equals(r.mesh.samples())&&loaded.record.structures().equals(r.record.structures()),"junction reload exact");}
    var reopened=Junctions.payload(l,null,points,id);var plan=JunctionPlanner.plan(JunctionCodec.read(reopened.getCompound("Spec")));h.assertTrue(plan.movements().stream().anyMatch(m->m.from()==0&&m.to()==1),"A to B persists");h.assertTrue(plan.movements().stream().anyMatch(m->m.from()==2&&m.to()==0),"C to A persists");h.assertTrue(plan.movements().stream().noneMatch(m->m.from()==1||m.to()==2),"no reversed one-way movements");
    Junctions.build(l,null,reopened);
   }
   Junctions.remove(l,null,id);for(var r:external){h.assertTrue(d.index.roads.containsKey(r.id()),"external road retained on junction deletion");d.remove(l,null,r.id());}
  }
  h.assertTrue(RoadWorkChunks.heldCount(l)==0,"work tickets released");h.succeed();
 }
}
