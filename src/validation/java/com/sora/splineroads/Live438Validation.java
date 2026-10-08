package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Actual exposed closure boundaries, including concave rail corners. */
public final class Live438Validation {
 static int checks;
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static double distance(Part p,V q){V a=p.a(),d=p.b().sub(a);double t=Math.max(0,Math.min(1,q.sub(a).dot(d)/d.dot(d)));return q.distance(a.add(d.mul(t)));}
 static void corners(){
  for(var style:List.of(Style.O3_ONE,Style.O6_RAIL))for(int slot=0;slot<RoadProfile.catalog(style).lanes();slot++){
   var raw=Live435Validation.road(style,Structure.BRIDGE);int sign=LanePoints.lane(raw,100,slot).sign();
   var mesh=Live435Validation.cut(raw,slot,sign>0?50:150,sign>0?150:50,true);
   var parts=RoadStructures.plan(mesh,Live435Validation.ground(0));
   var upper=parts.stream().filter(p->p.material()==Material.STEEL&&Math.abs(p.a().y()-21.05)<1e-6&&Math.abs(p.b().y()-21.05)<1e-6).toList();
   for(var cap:LaneDeck.caps(mesh)){
    V axis=cap.b().sub(cap.a()).horizontalUnit(),inward=axis.left().mul(RoadRailJoin.INSET);
    var at=RoadQueries.horizontal(mesh,cap.a().add(cap.b()).mul(.5)).sample();
    for(boolean first:new boolean[]{true,false}){
     V end=first?cap.a():cap.b();boolean outer=Math.abs(end.sub(at.center()).dot(at.left()))>=at.halfWidth()-1e-5;
     V corner=end.add(axis.mul((first?(outer?1:-1):(outer?-1:1))*RoadRailJoin.INSET)).add(inward).add(new V(0,1.05,0));
     check(upper.stream().anyMatch(p->distance(p,corner)<1e-5),"transverse rail fails to reach corner "+style+" slot="+slot+" at "+corner);
     if(!outer)check(upper.stream().anyMatch(p->Math.abs(p.a().z()-p.b().z())>.01&&distance(p,corner)<1e-5),"longitudinal rail stops before cap "+corner);
    }
   }
   for(int i=1;i<mesh.samples().size();i++){
    var a=mesh.samples().get(i-1);var b=mesh.samples().get(i);if(a.distance()<55||a.distance()>145)continue;
    for(var strip:LaneDeck.strips(mesh,a,b))for(int side:new int[]{-1,1}){
     if(!(side>0?strip.highWall():strip.lowWall())||strip.al().distance(strip.ar())<.5)continue;
     V p=(side>0?strip.al().add(strip.bl()):strip.ar().add(strip.br())).mul(.5).sub(a.left().mul(side*RoadRailJoin.INSET)).add(new V(0,1.05,0));
     check(upper.stream().anyMatch(part->distance(part,p)<1e-5),"unprotected exposed closure edge "+style+" slot="+slot+" at "+p);
    }
   }
  }
 }
 static void tightSeam(){for(boolean highway:new boolean[]{false,true})for(int side:new int[]{-1,1}){
  var raw=Live435Validation.road(highway?Style.C1_HIGHWAY_RAMP:Style.C1_RAMP,Structure.BRIDGE);
  var md=LanePoints.Data.EMPTY.openings(List.of(new LanePoints.Opening(new UUID(438,4),List.of(new V(0,20,0),new V(0,20,200)),2)));
  var own=RoadRibbon.mesh(raw.samples().stream().map(s->new Sample(s.center(),s.left(),s.distance(),2)).toList(),raw.settings().options(raw.settings().options().lanePoints(md)));
  double offset=highway?3.9:3.82;
  var neighbor=RoadRibbon.mesh(own.samples().stream().map(s->new Sample(s.center().add(new V(side*offset,.5,0)),s.left(),s.distance(),2)).toList(),raw.settings());
  var join=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(neighbor,true)));
  Ground ground=new Ground(){public double top(double x,double z,double y){return 0;}public boolean joined(V p){return false;}
   public List<RoadRailJoin.Span> railSpans(V a,V b,V outside){return join.exposed(a,b,outside);}
   public boolean blocked(Part p){return RoadClearance.structureInvades(p,neighbor,4.25);}};
  var legacy=new ArrayList<Part>();RoadStructures.barrier(legacy,new V(side*1.66,20,50),new V(side*1.66,20,50.5),highway,true,50);
  check(legacy.stream().anyMatch(ground::blocked),"fixture did not reproduce whole-assembly removal");
  var parts=RoadStructures.plan(own,ground);
  for(double z=2.25;z<198;z+=.5){V at=new V(side*1.66,highway?20.8:21.05,z);
   check(parts.stream().anyMatch(p->(highway?p.material()==Material.CONCRETE&&Math.abs(p.height()-.35)<1e-7:p.material()==Material.STEEL)&&distance(p,at)<1e-5),"gap in protected seam at "+at);
  }
  for(var p:parts)check(!ground.blocked(p),"fitted rail violates live neighbor clearance");
  check(parts.stream().anyMatch(p->p.material()==Material.CONCRETE&&p.width()<(highway?.62:.42)-1e-6),"no fitted concrete bearing");
 }}
 public static void main(String[] args){corners();tightSeam();System.out.println("Live438Validation: "+checks+" checks PASS; connected cap corners and tight graded-seam rails, production geometry");}
}
