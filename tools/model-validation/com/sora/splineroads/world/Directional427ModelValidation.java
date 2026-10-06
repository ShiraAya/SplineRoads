package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadProfile.*;
import net.minecraft.core.BlockPos;
import java.util.*;
/** Actual record codec and cross-section derivation; typed NBT/BlockPos adapters, no game. */
public final class Directional427ModelValidation {
 static int checks,cases; static long sequence;
 static UUID id(){return new UUID(427,++sequence);}
 static void check(boolean ok,String msg){checks++;if(!ok)throw new AssertionError(msg);}
 static Settings settings(Type type,int f,int r,boolean left){return RoadLanes.configure(new Settings(Mode.STRAIGHT,Style.O4_RAIL,18,1,.4,90).options(Options.DEFAULT.traffic(left).route(Routing.DEFAULT.fit(false))),type,new RoadLanes.Counts(f,r),type==Type.HIGHWAY?5:4);}
 static RoadRecord road(Settings s){return new RoadRecord(id(),id(),new BlockPos(0,8,0),new BlockPos(0,8,300),new Node(new V(.5,8,.5),0,0),new Node(new V(.5,8,300.5),0,0),s,true,4);}
 static void roundtrip(Settings s){
   cases++;var road=road(s);var restored=RoadRecord.load(road.save());
   check(RoadRecord.writeSettings(s).equals(RoadRecord.writeSettings(restored.settings())),"settings roundtrip");
   check(road.mesh().samples().equals(restored.mesh().samples()),"physical samples roundtrip");
   var port=RoadEndpointSections.section(road.mesh(),false,true);var decoded=RoadRecord.readSettings(RoadRecord.writeSettings(port));
   check(RoadLanes.counts(decoded).equals(RoadLanes.counts(port)),"port counts roundtrip");
   check(decoded.options().ends().port().equals(port.options().ends().port()),"port median/stripe fields roundtrip");
   var ended=RoadTransitions.ends(s,RoadTransitions.Section.of(decoded),RoadTransitions.Section.of(decoded));
   var re=RoadRecord.readSettings(RoadRecord.writeSettings(ended));
   check(RoadLanes.counts(re.options().ends().start().settings(false)).equals(RoadLanes.counts(decoded)),"start section counts roundtrip");
   check(re.options().ends().start().port().equals(decoded.options().ends().port()),"nested exact port roundtrip");
   for(int lane=0;lane<RoadLanes.counts(s).total();lane++)check(LanePoints.lane(restored.mesh(),100,lane).position().distance(LanePoints.lane(road.mesh(),100,lane).position())<1e-8,"lane centres roundtrip");
 }
 static void merged(Type type,int f,int r,boolean left,int sign){
   cases++;var h=road(settings(type,f,r,left));var m=h.rawMesh();int slot=-1;double furthest=-1;
   var l=RoadProfile.layout(m,m.first());
   for(int i=0;i<f+r;i++){var lane=LanePoints.lane(m,150,i);if(lane.sign()!=sign)continue;double d=Math.abs(lane.position().sub(RoadStructures.sample(m,150).center()).dot(m.first().left())-l.medianCenter());if(d>furthest){slot=i;furthest=d;}}
   var point=LanePoints.point(id(),LanePoints.Origin.MANUAL,m,150,slot).merge(24);
   h=h.withLanePoints(LanePoints.Data.EMPTY.points(List.of(point)));var all=new LinkedHashMap<UUID,RoadRecord>();all.put(h.id(),h);LaneCrossSections.reconcile(all);h=all.get(h.id());
   var at=sign<0?h.mesh().first():h.mesh().last();var section=RoadEndpointSections.section(h.caps(0).mesh(),sign<0,sign<0);
   var s=RoadEndpointSections.inherit(h.settings(),section);var counts=RoadLanes.counts(s);
   check(counts.total()==f+r-1,"live endpoint count after merge");check(s.options().lanePoints().equals(LanePoints.Data.EMPTY),"new road copied merge ownership");
   V dir=at.left().left().mul(sign<0?1:-1);var a=new Node(at.center(),RoadPlanner.yaw(dir),0);var b=new Node(at.center().add(dir.mul(140)),a.yaw(),0);
   var child=new RoadRecord(id(),h.owner(),sign<0?h.a():h.b(),new BlockPos(0,8,sign<0?-140:440),a,b,s,true,4);
   all.put(child.id(),child);LaneCrossSections.reconcile(all);
   check(all.get(h.id()).header().equals(h.header()),"continuation changed original merge");
   check(all.get(child.id()).mesh().first().center().distance(at.center())<1e-8,"new centre did not match actual port");
   check(Math.abs(all.get(child.id()).mesh().first().halfWidth()-at.halfWidth())<1e-8,"new width did not match actual port");
   check(RoadRecord.load(child.save()).settings().options().lanes().equals(counts),"child persisted stale count");
   all.remove(child.id());LaneCrossSections.reconcile(all);check(all.get(h.id()).header().equals(h.header()),"deleting continuation undid unrelated merge");
 }
 public static void main(String[]args){
   for(var type:List.of(Type.ORDINARY,Type.HIGHWAY))for(boolean left:new boolean[]{false,true})for(int f=1;f<=4;f++)for(int r=0;r<=4;r++)roundtrip(settings(type,f,r,left));
   for(var type:List.of(Type.ORDINARY,Type.HIGHWAY))for(boolean left:new boolean[]{false,true})for(int f=2;f<=4;f++)merged(type,f,0,left,1);
   for(boolean left:new boolean[]{false,true})for(int sign:new int[]{-1,1})merged(Type.ORDINARY,3,2,left,sign);
   for(var style:List.of(Style.O3_ONE,Style.O6_RAIL,Style.H6_GREEN)){
     var s=new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.4,90);var t=RoadRecord.writeSettings(s);check(!t.contains("DirectionalLanes"),"legacy wrote explicit count");
     check(!RoadRecord.readSettings(t).options().lanes().explicit(),"legacy became explicit");check(RoadEndpointSections.orient(RoadEndpointSections.orient(s,true),true).equals(s),"legacy mirror changed authored options");
   }
   System.out.println("Directional427ModelValidation: "+cases+" cases / "+checks+" checks PASS; production records and lane derivation, NBT adapters NOT Minecraft world transactions");
 }
}
