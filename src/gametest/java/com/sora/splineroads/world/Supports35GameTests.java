package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_supports35") @PrefixGameTestTemplate(false)
public final class Supports35GameTests {
 @GameTest(template="empty",templateNamespace="splineroads_supports35",timeoutTicks=12000)
 public static void portalSearchLoadsFoundationOutsideDeck(GameTestHelper h){
  var l=h.getLevel();var a=Revision32GameTests.marker(h,68000,15,68008,-90);var b=Revision32GameTests.marker(h,68048,15,68008,-90);
  var upper=new RoadRecord(UUID.randomUUID(),new UUID(0,0),a,b,new Node(new V(a.getX()+.5,15,a.getZ()+.5),-90,0),new Node(new V(b.getX()+.5,15,b.getZ()+.5),-90,0),Revision32GameTests.road(Style.O2_YELLOW,Structure.BRIDGE).options(RoadProfile.Options.DEFAULT.infrastructure(RoadInfrastructure.Config.DEFAULT.bridge(RoadInfrastructure.Bridge.OVERPASS))));
  var lower=new RoadRecord(UUID.randomUUID(),new UUID(0,0),a.below(13),b.below(13),new Node(upper.start().position().add(new V(0,-13,0)),-90,0),new Node(upper.end().position().add(new V(0,-13,0)),-90,0),new Settings(Mode.STRAIGHT,Style.O8_YELLOW,64,1,.4,90).structure(Structure.GROUND));
  var built=new RoadIndex.Built(upper);var below=new RoadIndex.Built(lower);
  try(var work=RoadWorkChunks.open(l)){
   work.roads(List.of(built));var planned=StructurePlanner.plan(l,built,List.of(built,below),Map.of(),new HashMap<>());
   long feet=planned.structures().stream().filter(p->p.pier()&&p.material()==Material.CONCRETE&&p.height()>2).count();
   h.assertTrue(feet>=4,"portal search must load foundations outside upper strip: "+feet);
   for(var part:planned.structures())if(part.material()==Material.CONCRETE)h.assertTrue(!RoadInteractions.invades(part,below.mesh),"portal preserves lower clearance");
  }
  h.assertTrue(RoadWorkChunks.heldCount(l)==0,"probe chunk tickets released");h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_supports35",timeoutTicks=12000)
 public static void autoPiersAndLowerJunctionRebuild(GameTestHelper h){
  var l=h.getLevel();var d=RoadData.get(l);int x=66000,z=66000;
  // Full terrain near the deck, including the portal search, but initially unloaded beyond it.
  var a=Revision32GameTests.marker(h,x,15,z,-90);var b=Revision32GameTests.marker(h,x+240,15,z,-90);
  var settings=Revision32GameTests.road(Style.O4_YELLOW,Structure.AUTO);
  var upper=d.connect(l,null,a,b,settings,null);var shafts=upper.structures().stream().filter(p->p.pier()&&p.material()==Material.CONCRETE&&p.height()>2).toList();
  h.assertTrue(shafts.size()>=10,"long AUTO has regular supports: "+shafts.size());
  var c=Revision32GameTests.marker(h,x-40,2,z,-90);var e=Revision32GameTests.marker(h,x+280,2,z,-90);
  var lower=d.connect(l,null,c,e,Revision32GameTests.road(Style.O8_YELLOW,Structure.GROUND),null);
  var up=d.index.roads.get(upper.id());h.assertTrue(up.record.structures().stream().filter(p->p.pier()&&p.material()==Material.CONCRETE&&p.height()>2).count()==0,"blocked automatic supports are omitted instead of forcing portals across lower road");
  for(var p:up.record.structures())if(p.material()==Material.CONCRETE)h.assertTrue(!RoadInteractions.invades(p,d.index.roads.get(lower.id()).mesh),"support clears lower lanes");
  d.remove(l,null,lower.id());
  var center=Revision32GameTests.marker(h,x+120,2,z,0);var w=Revision32GameTests.marker(h,x+50,2,z,-90);var east=Revision32GameTests.marker(h,x+190,2,z,90);var n=Revision32GameTests.marker(h,x+120,2,z-70,0);var s=Revision32GameTests.marker(h,x+120,2,z+70,180);
  long[] points={center.asLong(),w.asLong(),east.asLong(),n.asLong(),s.asLong()};var input=Junctions.payload(l,null,points,null);Junctions.build(l,null,input);
  UUID id=d.junctions.entrySet().stream().filter(entry->Arrays.equals(points,entry.getValue().getLongArray("Points"))).findFirst().orElseThrow().getKey();
  h.assertTrue(d.index.roads.get(upper.id()).record.structures().stream().anyMatch(p->p.pier()&&p.material()==Material.CONCRETE&&p.height()>2),"lower junction retains upper piers");
  Junctions.build(l,null,Junctions.payload(l,null,points,id));
  var saved=d.index.roads.get(upper.id()).record;h.assertTrue(RoadData.load(d.save(new CompoundTag())).index.roads.get(upper.id()).record.structures().equals(saved.structures()),"supports persist after lower junction edit");
  Junctions.remove(l,null,id);d.remove(l,null,upper.id());h.assertTrue(RoadWorkChunks.heldCount(l)==0,"work tickets released");h.succeed();
 }
}
