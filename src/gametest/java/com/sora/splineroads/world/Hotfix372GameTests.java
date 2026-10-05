package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.JunctionSpec.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("splineroads_hotfix372") @PrefixGameTestTemplate(false)
public final class Hotfix372GameTests {
 static Settings settings(Style style){var s=Revision32GameTests.road(style,Structure.GROUND);return RoadPlanner.mode(s,Mode.AUTO,s.arcDegrees());}
 @GameTest(template="empty",templateNamespace="splineroads_hotfix372",timeoutTicks=12000)
 public static void actualManualJunctionPortsAcceptSameLaneRoads(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);int offset=0;
  for(var style:List.of(Style.O3_ONE,Style.O6_YELLOW)){
   int x=132000+offset,z=132000;offset+=700;var s=settings(style);
   var center=Revision32GameTests.marker(h,x+100,2,z+100,0);var a=Revision32GameTests.marker(h,x,2,z+100,-90);var b=Revision32GameTests.marker(h,x+100,2,z+200,180);var c=Revision32GameTests.marker(h,x+200,2,z+100,90);var far=Revision32GameTests.marker(h,x-80,2,z+100,-90);
   long[] points={center.asLong(),a.asLong(),b.asLong(),c.asLong()};var request=Junctions.payload(level,null,points,null);Junctions.build(level,null,request);
   UUID group=data.junctions.entrySet().stream().filter(e->Arrays.equals(e.getValue().getLongArray("Points"),points)).findFirst().orElseThrow().getKey();
   // Use the real road editor: junction commands intentionally cannot replace external profiles.
   var approach=data.index.roads.values().stream().filter(r->group.equals(r.record.assembly())&&r.record.junction().get().arm()==0).findFirst().orElseThrow().record;
   var edit=JunctionRoads.payload(level,null,approach);edit.put("Settings",RoadRecord.writeSettings(s));JunctionRoads.build(level,null,edit);
   var port=data.endpointSettings(a,null);h.assertTrue(port!=null&&port.style()==style,"manual junction exposes actual "+style+" instead of a blank pad profile");
   var payload=new CompoundTag();data.jointPayload(payload,far,a,null);h.assertTrue(payload.getBoolean("FixedSectionB"),"physical junction mouth retained as a fixed port");
   var joined=RoadData.joinSections(s,payload);h.assertTrue(RoadProfile.catalog(joined.style()).lanes()==RoadProfile.catalog(style).lanes(),"preview retains requested lane count");
   var road=data.connect(level,null,far,a,s,null);h.assertTrue(data.index.roads.get(road.id()).record.settings().style()==style,"same-profile road actually built "+style);
   var loaded=RoadData.load(data.save(new CompoundTag()));h.assertTrue(loaded.endpointSettings(a,road.id()).style()==style,"actual port survives reload");
   data.connect(level,null,far,a,s,road.id());data.remove(level,null,road.id());Junctions.remove(level,null,group);
  }h.succeed();
 }
 @GameTest(template="empty",templateNamespace="splineroads_hotfix372",timeoutTicks=12000)
 public static void unmarkedLegacyPadDoesNotRejectOrdinaryContinuation(GameTestHelper h){
  var level=h.getLevel();var data=RoadData.get(level);int offset=0;
  for(var style:List.of(Style.O2_ONE,Style.O4_YELLOW)){
   int x=134000+offset,z=134000;offset+=500;var s=settings(style);
   var a=Revision32GameTests.marker(h,x,2,z,-90);var b=Revision32GameTests.marker(h,x+64,2,z,-90);var c=Revision32GameTests.marker(h,x+128,2,z,-90);
   var first=data.connect(level,null,a,b,s,null);
   // Older assemblies can retain a blank infill record sharing an editing marker.
   var padSettings=new Settings(Mode.STRAIGHT,Style.UNMARKED,s.width(),1,.4,90).structure(Structure.GROUND);
   var pad=new RoadRecord(new UUID(0,offset),new UUID(0,0),b,new BlockPos(x+68,2,z+4),new Node(new V(x+64.5,2,z+.5),-90,0),new Node(new V(x+68.5,2,z+4.5),-90,0),padSettings);
   h.assertTrue(RoadData.transitionEndpoint(new RoadIndex.Built(pad),b),"standalone legacy unmarked road remains connectable");
   pad=pad.alignment(new UUID(1,offset),pad.mesh());data.index.put(new RoadIndex.Built(pad));
   h.assertTrue(!RoadTransitions.compatible(s,padSettings),"blank legacy profile would fail ordinary compatibility if admitted");
   h.assertTrue(data.endpointSettings(b,null).style()==style,"infill never becomes the selected traffic profile");
   var next=data.connect(level,null,b,c,s,null);h.assertTrue(data.index.roads.containsKey(next.id()),"ordinary continuation built despite shared pad marker");
   var kept=data.index.roads.get(pad.id()).record;
   // Shared-node caps, structure clearance and furniture phase may be refreshed by a build.
   h.assertTrue(kept.id().equals(pad.id())&&kept.owner().equals(pad.owner())&&kept.a().equals(pad.a())&&kept.b().equals(pad.b())&&kept.start().equals(pad.start())&&kept.end().equals(pad.end())&&kept.settings().equals(pad.settings())&&kept.assembly().equals(pad.assembly())&&kept.alignment().equals(pad.alignment()),"infill geometry and authored profile preserved");
   data.index.remove(pad.id());data.remove(level,null,next.id());data.remove(level,null,first.id());
  }h.succeed();
 }
}
