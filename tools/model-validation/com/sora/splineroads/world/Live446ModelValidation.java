package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Four-image requirements using production geometry, with explicit world adapters. */
public final class Live446ModelValidation {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static LanePoints.Options options(boolean allow,LanePoints.Elevation elevation){return new LanePoints.Options(LanePoints.Path.DIRECT,LanePoints.Departure.BRANCH,LanePoints.Arrival.FLOW,24,32,elevation,LanePoints.Landing.EXACT,false,allow);}
 static Mesh ramp(double y,double length,boolean allow){
  var link=new LanePoints.Link(LanePoints.Ref.lane(new UUID(446,1),new UUID(446,2)),LanePoints.Ref.lane(new UUID(446,3),new UUID(446,4)),options(allow,LanePoints.Elevation.AUTO),null);
  var settings=LaneRampAlignment.usableWidth(new Settings(Mode.STRAIGHT,Style.C1_RAMP,4,1,.35,90).options(RoadProfile.Options.DEFAULT.lanePoints(LanePoints.Data.EMPTY.link(link))),4);
  return RoadGeometry.build(new Node(new V(0,y,0),0,0),new Node(new V(0,y,length),0,0),settings);
 }
 static Ground ground(java.util.function.ToDoubleFunction<V> height){return new Ground(){public double top(double x,double z,double at){return Math.min(at,height.applyAsDouble(new V(x,0,z)));}public double surface(double x,double z,double at){return height.applyAsDouble(new V(x,0,z));}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};}
 static void structures(){
  var ramp=ramp(20,60,true);
  check(Math.abs(ramp.first().halfWidth()-2.75)<1e-9&&Math.abs(ramp.last().halfWidth()-2.75)<1e-9,"implicit endpoint taper narrowed new ramp");
  for(double depth:new double[]{-10,0,.001,1,3.5,4.99,5,5.01,12}){
   var ground=ground(p->20+depth);var parts=RoadAutoTunnels.enclose(ramp,ground,List.of());
   check(parts.isEmpty()==(depth<=0),"lining iff below ground depth="+depth);
   var roofs=parts.stream().filter(p->p.material()==Material.TUNNEL&&p.height()<.1).toList();
   check(roofs.isEmpty()==(depth<5),"roof threshold depth="+depth);
   check(RoadAutoTunnels.regions(parts).isEmpty()==(depth<5),"closed interior metadata disagrees");
   if(depth>0&&depth<5){
    check(!RoadAutoTunnels.openRegions(parts).isEmpty(),"open cut has no excavation reservation");
    for(var p:parts){check(p.material()==Material.TUNNEL,"open cut acquired roof lamp");check(Math.abs(p.a().y()+p.height()-(20+depth+.5))<1e-6,"wall lip does not follow ground");}
   }
   for(var roof:roofs)check(Math.abs(roof.a().y()-24)<1e-6,"roof encroaches four-block headroom");
  }
  var mixed=ground(p->p.z()<10?0:p.z()<20?20:p.z()<40?23:26);
  var parts=RoadAutoTunnels.enclose(ramp,mixed,List.of());
  check(parts.stream().noneMatch(p->p.a().z()<20-1e-6),"above-ground part got tunnel walls");
  check(parts.stream().anyMatch(p->p.a().z()>=20&&p.a().z()<39&&p.height()>4),"shallow cut lacks walls");
  check(parts.stream().anyMatch(p->p.a().z()>41&&p.height()<.1&&p.material()==Material.TUNNEL),"deep part lacks roof");
  var exposed=RoadAutoTunnels.enclose(ramp,ground(p->p.x()>2.9?23:26),List.of());
  check(RoadAutoTunnels.regions(exposed).isEmpty(),"roof corner touching outside still capped");
  check(!exposed.isEmpty(),"open hillside lacks retaining walls");
  var opening=RoadAutoTunnels.enclose(ramp,ground(p->p.x()>.8&&p.x()<1.4?23:26),List.of());
  check(RoadAutoTunnels.regions(opening).isEmpty(),"narrow opening above roof missed between cover probes");
 }
 static void earthPolicy(){
  var road=ramp(20,200,false);var hill=ground(p->p.z()>70&&p.z()<130?23:19);
  var o=options(false,LanePoints.Elevation.UNDER);
  check(!LaneRampTerrain.allowed(road,o,hill),"UNDER implicitly permits tunnels");
  double[] floor=LaneRampTerrain.floors(road,o,hill);check(!LaneRampTerrain.clear(road,floor),"hill crossing accepted with toggle off");
  var raised=LaneRampCorridor.solveMixed(road,0,road.length(),List.of(),.2,false,floor);
  check(LaneRampTerrain.clear(raised,LaneRampTerrain.floors(raised,o,hill)),"terrain constrained solver still buried");
  check(raised.first().center().equals(road.first().center())&&raised.last().center().equals(road.last().center()),"earth policy moved fixed ports");
  check(LaneRampTerrain.allowed(road,options(true,LanePoints.Elevation.OVER),hill),"OVER cannot include allowed underground segment");
  check(LaneRampTerrain.allowed(road,o,ground(p->p.z()<10?26:19)),"underground departure exception lost");
  check(LaneRampTerrain.allowed(road,o,ground(p->p.z()>190?26:19)),"underground arrival exception lost");
  check(!LanePointCodec.options(new net.minecraft.nbt.CompoundTag()).allowTunnel(),"default toggle enabled");
  var enabled=options(true,LanePoints.Elevation.UNDER);check(LanePointCodec.options(LanePointCodec.options(enabled)).equals(enabled),"toggle lost in NBT");
  check(enabled.withoutApproaches().allowTunnel(),"route helper lost tunnel policy");
  check(LanePoints.supported(road.settings().structure(Structure.TUNNEL)),"underground road cannot provide lane points");
 }
 static void widths(){
  for(var kind:List.of(Style.C1_RAMP,Style.C1_HIGHWAY_RAMP)){
   var raw=ramp(20,120,false);raw=RoadRibbon.mesh(raw.samples(),new Settings(Mode.STRAIGHT,kind,raw.settings().width(),1,.35,90).options(raw.settings().options()));
   var m=LaneRampAlignment.fit(raw,new LaneRampAlignment.Mouth(4,0,.5),new LaneRampAlignment.Mouth(4,0,3.5));
   double previous=Double.NaN;
   for(var at:m.samples()){
    var layout=RoadProfile.layout(m,at);double lo=RoadSurface.edgeOffset(m,at,-1),hi=RoadSurface.edgeOffset(m,at,1);
    check(Math.abs((hi-lo)-4)<1e-6,"shoulder counted as motor lane width");
    check(Math.abs(lo-layout.motorMin())<1e-6&&Math.abs(hi-layout.motorMax())<1e-6,"lane edge and mouth disagree");
    if(Double.isFinite(previous))check(Math.abs(at.halfWidth()-previous)<.15,"sudden pavement widening at junction");previous=at.halfWidth();
   }
  }
  var settings=LaneRampAlignment.usableWidth(new Settings(Mode.STRAIGHT,Style.C1_HIGHWAY_RAMP,4,1,.35,90).structure(Structure.BRIDGE),4);
  var highway=RoadGeometry.build(new Node(new V(0,20,0),0,0),new Node(new V(0,20,60),0,0),settings);
  var at=highway.first();var p=at.at(at.halfWidth()-RoadRailJoin.INSET,0);
  var join=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(highway,true)));
  check(join.joint(p,new V(.2,0,1),true,true)!=null,"highway ramp rail lost its matching junction profile");
 }
 static void auxiliaryWidths(){
  for(boolean highway:new boolean[]{false,true}){
   var settings=RoadLanes.configure(new Settings(Mode.STRAIGHT,highway?Style.H3_ONE:Style.O3_ONE,20,1,.35,90).structure(Structure.BRIDGE),highway?RoadProfile.Type.HIGHWAY:RoadProfile.Type.ORDINARY,new RoadLanes.Counts(3,0),4);
   var source=Hotfix429ModelValidation.road(new V(1400,20,-700),new V(1400,20,200),settings);
   var target=Hotfix429ModelValidation.road(new V(1260,20,250),new V(1260,20,1000),settings);
   var all=new LinkedHashMap<UUID,RoadRecord>();all.put(source.id(),source);all.put(target.id(),target);
   var a=Hotfix429ModelValidation.point(all,source,200,2);var b=Hotfix429ModelValidation.point(all,target,250,2);
   var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.EXTRA,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
   var ramp=LaneRamps.generate(null,all,new UUID(446,highway?90:91),source.owner(),new LanePoints.Link(a,b,options,null));var mesh=ramp.mesh();
   for(int i=1;i<mesh.samples().size();i++){
    var p=mesh.samples().get(i-1);var q=mesh.samples().get(i);
    check(Math.abs(p.halfWidth()-q.halfWidth())<.16,"auxiliary merge has abrupt pavement width step");
    check(Math.abs(RoadProfile.layout(mesh,q).laneWidth()-4)<1e-5,"auxiliary merge changes motor lane width");
   }
   check(RoadRecord.load(ramp.header()).mesh().samples().equals(mesh.samples()),"auxiliary width changes after reload");
  }
 }
 public static void main(String[] args){structures();earthPolicy();widths();auxiliaryWidths();System.out.println("Live446ModelValidation: "+checks+" checks PASS; width bands, open/roofed cuts, original terrain constraints and option roundtrip (not Minecraft/GPU)");}
}
