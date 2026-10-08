package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
public final class Live437Validation {
 static int checks;
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void supports(){
  var settings=Live435Validation.road(Style.C1_RAMP,Structure.BRIDGE).settings();
  for(double angle:new double[]{0,.7,Math.PI/2,2.4})for(double grade:new double[]{-.2,-.08,0,.12,.25}){
   V direction=new V(Math.sin(angle),0,Math.cos(angle));var samples=new ArrayList<Sample>();
   for(double d=0;d<=100;d+=.25)samples.add(new Sample(new V(100,30,200).add(direction.mul(d)).add(new V(0,grade*d,0)),direction.left(),d,2));
   var mesh=RoadRibbon.mesh(samples,settings);double station=mesh.length()/2;var at=RoadStructures.sample(mesh,station);
   var parts=RoadSupports.ramp(mesh,station,Live435Validation.ground(0));check(parts.size()==1,"slope support absent");var p=parts.get(0);
   for(var v:p.base()){
    double expected=at.center().y()+v.sub(at.center()).dot(direction)*grade-settings.thickness()-.006;
    check(Math.abs(v.y()+p.height()-expected)<1e-6,"pier top does not follow underside plane");
    check(v.y()<=.000001,"pier foundation floats at slope edge");
   }
   check(!RoadClearance.structureInvades(p,mesh,4.25),"pier clips its own deck");
  }
 }
 static void fascia(){
  var mesh=Live435Validation.road(Style.C1_RAMP,Structure.BRIDGE);
  Ground ground=new Ground(){public double top(double x,double z,double y){return 0;}public boolean blocked(Part p){return false;}public boolean joined(V p){return true;}
   public List<RoadRailJoin.Span> railSpans(V a,V b,V outside){return List.of(new RoadRailJoin.Span(a,b));}};
  var parts=RoadStructures.edgeSlabs(mesh,ground);check(!parts.isEmpty(),"broad furniture opening erased exposed fascia");
  for(int side:new int[]{-1,1})check(parts.stream().anyMatch(p->p.a().x()*side>mesh.first().halfWidth()),"missing outward fascia side");
 }
 static void caps(){
  var source=Live435Validation.road(Style.O3_ONE,Structure.BRIDGE);
  var raw=RoadRibbon.mesh(source.samples().stream().map(s->new Sample(s.center(),s.left().mul(-1),s.distance(),s.halfWidth())).toList(),source.settings());
  var c1=new LaneSections.Cut(new UUID(437,1),1,1,50,120,32,null,true,false,true);
  var c2=new LaneSections.Cut(new UUID(437,2),1,1,80,150,32,null,true,true,true);
  var mesh=LaneSections.apply(RoadRibbon.mesh(raw.samples(),raw.settings().options(raw.settings().options().lanePoints(LanePoints.Data.EMPTY.cuts(List.of(c1,c2))))));
  check(LaneDeck.caps(mesh).size()==2,"overlap produces interior transverse rail cap");
  var parts=RoadStructures.plan(mesh,Live435Validation.ground(0));
  for(var p:parts)if(!p.pier()&&Math.abs(p.a().z()-p.b().z())<1e-7&&p.material()==Material.STEEL&&p.a().y()<22&&p.height()<.2){
   for(V v:p.base())check(v.z()<50||v.z()>150,"transverse rail sits over empty lane");
  }
 }
 static void inactiveAddedSlot(){
  var raw=Live435Validation.road(Style.O3_ONE,Structure.BRIDGE);
  var addition=new LaneAdditions.Addition(new UUID(437,5),8,1,190,32);
  var cut=new LaneSections.Cut(new UUID(437,6),2,1,50,150,32,null,true,false,true);
  var md=LanePoints.Data.EMPTY.additions(List.of(addition)).cuts(List.of(cut));
  var mesh=LaneSections.apply(RoadRibbon.mesh(raw.samples(),raw.settings().options(raw.settings().options().lanePoints(md))));
  check(LaneDeck.outerOpening(mesh,100,1),"inactive future added slot leaves ghost shoulder and rail");
 }
 static void smooth(){
  var settings=Live435Validation.road(Style.C1_RAMP,Structure.BRIDGE).settings();
  V a=new V(0,20,0),b=new V(120,28,260),da=new V(0,0,1),db=new V(1,0,0);
  for(var elevation:LanePoints.Elevation.values()){
   var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.BRANCH,LanePoints.Arrival.FLOW,24,32,elevation,LanePoints.Landing.EXACT);
   var candidates=LaneRampPaths.smoothTurns(new LaneRampPaths.Port(a,da,da.left(),0,0),new LaneRampPaths.Port(b,db,db.left(),0,0),settings,options,.2);
   check(!candidates.isEmpty(),"smooth route unavailable for height mode "+elevation);
   var m=candidates.get(0).mesh();check(m.length()<a.sub(b).horizontalLength()*1.35,"forward bend unnecessarily large");
   double straight=0;for(int i=1;i<m.samples().size();i++)if(m.samples().get(i).left().dot(m.samples().get(i-1).left())>1-1e-10)straight+=m.samples().get(i).center().distance(m.samples().get(i-1).center());
   check(straight<m.length()*.02,"smooth bend dominated by straight segments");
  }
 }
 public static void main(String[] args){supports();fascia();caps();inactiveAddedSlot();smooth();System.out.println("Live437Validation: "+checks+" checks PASS; production geometry, no GPU");}
}
