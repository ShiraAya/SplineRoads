package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
/** Synthetic counterparts of the save defects; no original world/player data. */
public final class Live445ModelValidation {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static Settings settings(Style style,double width){return new Settings(Mode.STRAIGHT,style,width,1,.35,90).options(RoadProfile.Options.DEFAULT.hideArrows(true));}
 static void addedBoundary(){
  var road=Hotfix429ModelValidation.road(new V(0,20,0),new V(0,20,500),settings(Style.O6_GREEN,28));var id=new UUID(445,1);
  var cuts=List.of(new LaneSections.Cut(new UUID(445,2),5,1,20,560,32),new LaneSections.Cut(new UUID(445,3),4,1,60,560,32,null,false,false,true),new LaneSections.Cut(id,8,1,368,400,32,null,true,true,true));
  road=road.withLanePoints(LanePoints.Data.EMPTY.cuts(cuts).additions(List.of(new LaneAdditions.Addition(id,8,1,400,32))));var m=road.mesh();
  var before=RoadStructures.sample(m,367);double edge=before.at(before.halfWidth(),0).x();
  for(var at:m.samples()){double d=at.distance();if(d<=368||d>=400)continue;double actual=LaneDeck.spans(m,at).stream().filter(s->s.high()-s.low()>.01).mapToDouble(s->at.at(s.high(),0).x()).min().orElseThrow();check(actual<=edge+1e-6,"ADD removes the old straight shoulder at "+d+": "+actual+" / "+edge);}
  for(double d=402;d<490;d+=1){var at=RoadStructures.sample(m,d);var l=RoadProfile.layout(m,at);for(double divider:l.dividers())if(divider>l.motorMin()+.2&&divider<l.motorMax()-.2)check(!RoadSurface.closedSlotBoundary(m,d,divider),"old DETACH makes the new ADD divider solid");}
 }
 static void temporary(int slot){temporary(slot,LanePoints.Elevation.OVER);}
 static void temporary(int slot,LanePoints.Elevation elevation){
  var source=Hotfix429ModelValidation.road(new V(0,108,0),new V(500,108,0),settings(Style.O6_RAIL,26));
  var target=Hotfix429ModelValidation.road(new V(250,100,-250),new V(250,100,250),settings(Style.O6_GREEN,28));
  var all=new LinkedHashMap<UUID,RoadRecord>();all.put(source.id(),source);all.put(target.id(),target);
  var a=Hotfix429ModelValidation.point(all,source,100,slot);var b=Hotfix429ModelValidation.point(all,target,400,3);
  var options=new LanePoints.Options(LanePoints.Path.RIGHT,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.ADD,24,32,elevation,LanePoints.Landing.FLEXIBLE);
  long start=System.nanoTime();var ramp=LaneRamps.generate(null,all,new UUID(445,100+slot),source.owner(),new LanePoints.Link(a,b,options,null));all.put(ramp.id(),ramp);LaneCrossSections.reconcile(all);LaneRamps.validate(ramp.mesh(),all,ramp.id(),LaneTopology.metadata(ramp).link());
  check(ramp.settings().width()>=5.5,"independent pavement lacks rail allowance");check(RoadProfile.layout(ramp.settings(),ramp.settings().width()).laneWidth()>=4,"nominal drive corridor narrower than four");
  var lane=LanePoints.lane(source.mesh(),LaneTopology.point(all.get(source.id()),a.point()));check(LaneRampAlignment.axis(ramp.mesh(),true).distance(lane.position())<1e-5,"fixed starting motor axis moved");
  check(RoadRecord.load(ramp.header()).mesh().samples().equals(ramp.mesh().samples()),"wide connector shifts after reload");
  var copy=LaneRamps.reconfigure(ramp,ramp.settings(),all);check(copy.mesh().samples().equals(ramp.mesh().samples()),"repeated preview drifts the wide connector");
  System.out.printf(Locale.ROOT,"LIVE445 TEMPORARY slot=%d elevation=%s ms=%.2f length=%.2f%n",slot,elevation,(System.nanoTime()-start)/1e6,ramp.mesh().length());
 }
 static void ownGirders(){
  var s=LaneRampAlignment.usableWidth(settings(Style.C1_RAMP,4),4);
  var flat=RoadGeometry.build(new Node(new V(0,20,0),0,0),new Node(new V(0,20,80),0,0),s);
  var welded=new RoadStructures.Part(new V(0,18.5,20),new V(0,18.5,22),1.1,.9,false,RoadStructures.Material.CONCRETE);
  check(!RoadInteractions.selfSupportBlocked(welded,flat),"own girder cannot weld into underside");
  var obstruction=new RoadStructures.Part(new V(0,22,20),new V(0,22,22),1.1,.9,false,RoadStructures.Material.CONCRETE);
  check(RoadInteractions.selfSupportBlocked(obstruction,flat),"overhead beam lost actual vehicle clearance");
  var loopSamples=List.of(new Sample(new V(0,10,0),new V(-1,0,0),0,2.75),new Sample(new V(0,10,40),new V(-1,0,0),40,2.75),new Sample(new V(40,15,60),new V(-1,0,0),80,2.75),new Sample(new V(0,20,40),new V(1,0,0),120,2.75),new Sample(new V(0,20,0),new V(1,0,0),160,2.75));
  var loop=new Mesh(loopSamples,s,new V(-3,9,0),new V(43,20,63),160,false);
  var between=new RoadStructures.Part(new V(0,13,20),new V(0,13,22),1.1,.9,false,RoadStructures.Material.CONCRETE);
  check(RoadInteractions.selfSupportBlocked(between,loop),"upper bearing exempted a lower leg of its own ramp");
 }
 static void ordinaryEdit(){
  var road=Hotfix429ModelValidation.road(new V(0,20,0),new V(0,20,300),settings(Style.O6_GREEN,28));
  var raw=road.mesh();var path=new ArrayList<Sample>();
  for(var p:raw.samples())path.add(new Sample(p.center().add(new V(0,Math.pow(Math.sin(Math.PI*p.distance()/raw.length()),2)*6,0)),p.left(),p.distance(),p.halfWidth()));
  road=road.alignment(null,RoadRibbon.mesh(path,road.settings()));
  var settings=road.settings().options(road.settings().options().hideArrows(false));
  var proposal=new RoadRecord(road.id(),road.owner(),road.a(),road.b(),road.start(),road.end(),settings,true,4);
  var retained=road.retainAlignment(proposal);check(retained.alignment().equals(road.alignment()),"ordinary edit discards saved vertical path");
  var moved=new RoadRecord(road.id(),road.owner(),road.a(),road.b(),road.start(),new Node(road.end().position().add(new V(2,0,0)),0,0),settings,true,4);
  check(road.retainAlignment(moved).alignment().isEmpty(),"changed endpoint reuses stale alignment");
 }
 static void width(){
  for(var style:List.of(Style.C1_RAMP,Style.C1_HIGHWAY_RAMP)){
   var s=LaneRampAlignment.usableWidth(settings(style,4),4);var m=RoadGeometry.build(new Node(new V(0,40,0),0,0),new Node(new V(0,40,120),0,0),s);
   double footing=RoadProfile.highway(style)?.31:.21;check(s.width()-2*(RoadRailJoin.INSET+footing)>=4,"four clear blocks between footings");
   check(RoadProfile.layout(m,m.first()).laneWidth()==4,"motor width includes the rail shoulders");
  }
 }
 public static void main(String[] args){addedBoundary();width();ownGirders();ordinaryEdit();temporary(5);temporary(4);temporary(5,LanePoints.Elevation.AUTO);System.out.println("Live445ModelValidation: "+checks+" checks PASS (production geometry/planner with test adapters, not Minecraft)");}
}
