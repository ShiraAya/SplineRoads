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
 public static void main(String[]args){liveLaneRail();pavedGroundClosure();System.out.println("Live441Validation: "+checks+" checks PASS; live-lane rail clearance, ground closure pavement in VBO/terrain and buried-lid protection");}
}
