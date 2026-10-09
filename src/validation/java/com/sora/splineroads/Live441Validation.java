package com.sora.splineroads;
import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadGeometry.*;import com.sora.splineroads.core.RoadStructures.*;import java.util.*;
public final class Live441Validation {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void liveLaneRail(){
  for(var style:List.of(Style.O3_ONE,Style.H4_RAIL)){
   var raw=Live435Validation.road(style,Structure.BRIDGE);var slot=LaneSections.live(raw,100).lanes().stream().filter(l->l.sign()==1&&LaneSections.edge(raw,100,l.index())).findFirst().orElseThrow().index();
   var host=Live435Validation.cut(raw,slot,40,160,false);var parts=RoadStructures.plan(host,Live435Validation.ground(0));
   var closed=LanePoints.lane(raw,100,slot);
   for(var lane:LaneSections.live(raw,100).lanes())if(lane.index()!=slot&&Math.abs(lane.position().distance(closed.position())-lane.width())<1e-6){
    var samples=new ArrayList<Sample>();for(double d=50;d<=150;d++){var at=LanePoints.lane(raw,d,lane.index());samples.add(new Sample(at.position(),RoadStructures.sample(raw,d).left(),d-50,at.width()/2-.001));}
    var travel=RoadRibbon.mesh(samples,new Settings(Mode.STRAIGHT,Style.C1_RAMP,lane.width()-.002,1,.35,90));
    check(parts.stream().filter(p->!p.pier()&&Math.abs(p.a().y()-20)<1e-6&&p.height()<2).noneMatch(p->RoadClearance.structureInvades(p,travel,4.25)),"closed-slot rail invaded a full-width adjacent lane: "+style+" slot "+lane.index());
   }
  }
 }
 static void pavedGroundClosure(){
  var raw=Live435Validation.road(Style.O3_ONE,Structure.GROUND);var host=Live435Validation.cut(raw,1,40,160,true);
  Ground low=new Ground(){public double top(double x,double z,double y){return 19.8;}public boolean joined(V p){return false;}public boolean blocked(Part p){return p.material()==Material.GREEN||p.material()==Material.SOIL;}};
  var parts=LaneClosureLandscape.plan(host,low);
  check(parts.stream().allMatch(LaneClosureLandscape::paved)&&parts.size()==120,"low ground closure did not become ordinary pavement");
  var geometry=RoadSurface.closurePavement(RoadSurface.build(host,List.of(),List.of()),parts,List.of(host));
  var point=LanePoints.lane(raw,100,1).position();
  check(geometry.pavement().stream().anyMatch(f->f.texture()==RoadSurface.Texture.PLAIN&&JunctionPaint.inside(f.points(),point)),"asphalt face missing in former planter slot");
  var terrain=RoadTerrainMesh.build(geometry);
  check(terrain.cells().containsKey(new RoadTerrainMesh.Cell((int)Math.floor(point.x()),19,100)),"terrain backend omitted closed-slot asphalt");
  var layers=RoadRenderMesh.layers(geometry,parts);
  check(layers.values().stream().flatMap(l->l.surface().pavement().stream()).anyMatch(f->JunctionPaint.inside(f.points(),point)),"VBO backend omitted closed-slot asphalt");
  check(RoadRenderMesh.structureFaces(parts,false).stream().noneMatch(f->RoadLighting.normal(f).y()>.9&&JunctionPaint.inside(f.points(),point)),"concrete top overlaps the asphalt fill");
  var pad=parts.get(50);check(RoadClearance.belowSurface(pad,raw,.025),"coplanar pavement union rejected");
  var below=RoadRibbon.mesh(raw.samples().stream().map(s->new Sample(s.center().add(new V(0,-1,0)),s.left(),s.distance(),s.halfWidth())).toList(),raw.settings());
  check(!RoadClearance.belowSurface(pad,below,.025),"pavement lid allowed over a buried road");
  check(LaneClosureLandscape.plan(host,Live435Validation.ground(19.8)).stream().anyMatch(p->p.material()==Material.GREEN),"unobstructed ground planting disappeared");
 }
 static void girderClearance(){
  // Keep the historical plain-deck test separate from a connector fascia.
  var s=new Settings(Mode.STRAIGHT,Style.O1_ONE,4,1,.4,90);
  var ground=RoadRibbon.mesh(List.of(new Sample(new V(-20,0,0),new V(0,0,1),0,2),new Sample(new V(20,0,0),new V(0,0,1),40,2)),s);
  var upper=RoadRibbon.mesh(List.of(new Sample(new V(0,5.2,-20),new V(-1,0,0),0,2),new Sample(new V(0,5.2,20),new V(-1,0,0),40,2)),s);
  check(RoadClearance.contacts(upper,ground).stream().noneMatch(RoadClearance.Contact::blocked),"plain slab fixture is not clear");
  var bridge=s.structure(Structure.BRIDGE).options(s.options().infrastructure(s.options().infrastructure().bridge(RoadInfrastructure.Bridge.OVERPASS)));
  var beam=RoadRibbon.mesh(upper.samples(),bridge);
  check(RoadClearance.contacts(beam,ground).stream().anyMatch(RoadClearance.Contact::blocked),"solver ignored the 0.9-block overpass girder");
  check(RoadClearance.contacts(beam,ground).stream().mapToDouble(RoadClearance.Contact::raise).max().orElse(0)>.79,"solver did not request sufficient bridge lift");
  var marginal=RoadRibbon.mesh(upper.samples().stream().map(p->new Sample(p.center().add(new V(0,.8,0)),p.left(),p.distance(),p.halfWidth())).toList(),bridge);
  check(RoadClearance.contacts(marginal,ground).stream().anyMatch(c->c.blocked()&&Math.abs(c.required()-4.25)<1e-7),"bridge solver accepted 4.1m while final beam validation requires 4.25m");
 }
 static void footingClearance(){
  var raw=Live435Validation.road(Style.O3_ONE,Structure.BRIDGE);var host=Live435Validation.cut(raw,1,40,160,false);
  var lane=LanePoints.lane(raw,100,1);var at=RoadStructures.sample(raw,100);
  V p=lane.position().add(at.left().mul(lane.width()/2-.2));
  check(LaneDeck.present(host,at,p.sub(at.center()).dot(at.left()),0),"fixture lacks the physical rail footing ledge");
  var part=new Part(p,p.add(new V(0,0,.3)),.1,1,false,Material.CONCRETE);
  check(!RoadClearance.structureInvades(part,host,4.25),"closed-slot footing treated as a vehicle lane");
  check(RoadClearance.structureContacts(part,host,4.25).stream().noneMatch(RoadClearance.Contact::blocked),"solver still treats closed-slot footing as traffic");
  var restored=new Part(p.add(new V(0,0,80)),p.add(new V(0,0,80.3)),.1,1,false,Material.CONCRETE);
  check(RoadClearance.structureInvades(restored,host,4.25),"restored downstream lane lost clearance protection");
  var adjacent=LanePoints.lane(raw,100,0).position();
  check(RoadClearance.structureInvades(new Part(adjacent,adjacent.add(new V(0,0,.3)),.1,1,false,Material.CONCRETE),host,4.25),"adjacent live lane lost clearance protection");
 }
 static void bridgeClosure(){
  var base=Live435Validation.road(Style.O3_ONE,Structure.BRIDGE);
  var cfg=base.settings().options();var settings=base.settings().options(cfg.infrastructure(cfg.infrastructure().bridge(RoadInfrastructure.Bridge.OVERPASS)));
  var raw=RoadRibbon.mesh(base.samples(),settings);var host=Live435Validation.cut(raw,0,40,160,false);
  var parts=RoadInfrastructure.plan(host,Live435Validation.ground(0));int checked=0;
  for(var p:parts)if(!p.pier()&&Math.abs(p.height()-.9)<1e-6&&p.a().distance(p.b())<3&&p.a().z()>45&&p.b().z()<155){
   var q=RoadQueries.horizontal(raw,p.a().add(p.b()).mul(.5));var lane=LanePoints.lane(raw,q.sample().distance(),0);
   for(var v:p.base())check(Math.abs(v.sub(lane.position()).dot(q.sample().left()))>=lane.width()/2-1e-6,"closed lane retained a custom bridge girder");checked++;
  }
  check(checked>0,"bridge fixture erased all remaining girders");
 }
 public static void main(String[]args){liveLaneRail();pavedGroundClosure();girderClearance();bridgeClosure();footingClearance();System.out.println("Live441Validation: "+checks+" checks PASS; live-lane rail clearance, ground closure pavement in VBO/terrain and buried-lid protection");}
}
