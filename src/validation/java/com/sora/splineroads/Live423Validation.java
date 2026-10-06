package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Screenshot-derived invariants, real core with explicit terrain fixtures, not GPU/world tests. */
public final class Live423Validation {
 static int checks,cases;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static void near(double a,double b,String m){check(Math.abs(a-b)<1e-5,m+": "+a+" / "+b);}
 static Mesh path(double ox,double oy,double yaw,double sign,boolean wobble){
  var s=new Settings(Mode.STRAIGHT,Style.C1_RAMP,4,1,.35,90);var p=new ArrayList<Sample>();
  for(int i=0;i<=307;i++){
   double t=i/307.,y=8*t*t*(3-2*t);
   if(wobble&&i>45&&i<240)y+=1.8*Math.pow(Math.sin(Math.PI*(i-45)/195.),2)*Math.sin(4*Math.PI*(i-45)/195.);
   V direction=new V(Math.sin(yaw),0,Math.cos(yaw));
   p.add(new Sample(new V(ox+direction.x()*i,oy+sign*y,-ox+direction.z()*i),direction.left(),i,2));
  }return RoadRibbon.mesh(p,s);
 }
 static void corridor(){
  for(double origin:new double[]{0,100000})for(double yaw:new double[]{0,.7})for(double sign:new double[]{-1,1})for(boolean wobble:new boolean[]{false,true}){
   cases++;var base=path(origin,origin==0?10:340,yaw,sign,wobble);
   // The real constraint needs a local clearance change, not a full-height hump.
   var cs=List.of(new LaneRampHeights.Constraint(90,120,.5));
   var m=LaneRampCorridor.solve(base,0,base.length(),cs,sign>0,.2);
   near(m.first().center().y(),base.first().center().y(),"source moved");near(m.last().center().y(),base.last().center().y(),"destination moved");
   double travel=0;for(int i=1;i<m.samples().size();i++){
    var a=m.samples().get(i-1);var b=m.samples().get(i);double dy=b.center().y()-a.center().y();travel+=Math.abs(dy);
    check(sign*dy>=-1e-6,"meaningless rise/fall remains in feasible monotone corridor");
    check(!LaneRampGrade.exceeds(dy,b.center().sub(a.center()).horizontalLength(),.2),"local grade bypassed");
    if(b.distance()>=90&&b.distance()<=120)check(sign*(b.center().y()-base.samples().get(i).center().y())>=.5-1e-6,"clearance lost");
   }near(travel,8,"extra vertical travel");
  }
  // Real obstacle higher than both endpoints does require a crest, still feasible.
  var base=path(0,20,0,1,false);var m=LaneRampCorridor.solve(base,0,base.length(),List.of(new LaneRampHeights.Constraint(125,155,12)),true,.2);
  check(m.samples().stream().mapToDouble(s->s.center().y()).max().orElseThrow()>base.last().center().y()+1,"real overpass incorrectly flattened");cases++;
  boolean rejected=false;try{LaneRampCorridor.solve(base,0,base.length(),List.of(new LaneRampHeights.Constraint(304,307,12)),true,.2);}catch(IllegalArgumentException e){rejected=true;check(e.getMessage().contains("固定接头"),"failure must identify fixed port");}check(rejected,"unsafe fixed-port collision accepted");cases++;
 }
 static Mesh deck(double x,double y,double width){return RoadGeometry.build(new Node(new V(x,y,0),0,0),new Node(new V(x,y,120),0,0),new Settings(Mode.STRAIGHT,width<7?Style.C1_RAMP:Style.O3_ONE,width,1,.35,90).structure(Structure.BRIDGE));}
 static double length(List<RoadRailJoin.Span> ss){return ss.stream().mapToDouble(s->s.a().distance(s.b())).sum();}
 static void railUnion(){
  for(double overlap:new double[]{0,.05,.15,.25})for(double height:new double[]{0,8}){
   var own=deck(0,10,12);var s=RoadStructures.sample(own,20);var t=RoadStructures.sample(own,100);
   V a=s.at(6-.16,0),b=t.at(6-.16,0),outside=a.add(b).mul(.5).add(s.left().mul(.4));
   var other=deck(s.left().x()*(8-overlap),10+height,4);var join=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(other,true)));
   near(length(join.exposed(a,b,outside)),height==0?0:80,"shared added-lane barrier or overpass erased rail");cases++;
  }
  var own=deck(0,10,12);var s=RoadStructures.sample(own,20);var t=RoadStructures.sample(own,100);V a=s.at(5.84,0),b=t.at(5.84,0);
  var gap=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(deck(s.left().x()*8.05,10,4),true)));near(length(gap.exposed(a,b,a.add(b).mul(.5).add(s.left().mul(.4)))),80,"real hole lost rail");cases++;
 }
 static Ground ground(boolean foliage){return new Ground(){public double top(double x,double z,double y){return y-1;}public boolean blocked(Part p){return foliage&&p.material()==Material.GREEN&&p.a().z()<50.3&&p.b().z()>49.7;}public boolean joined(V p){return false;}};}
 static Mesh closed(Structure structure){
  var raw=deck(0,10,13);var opt=raw.settings().options();
  var c=new LaneSections.Cut(new UUID(423,1),1,1,30,90,32,null,true,true,true);
  return LaneSections.apply(RoadRibbon.mesh(raw.samples(),raw.settings().structure(structure).options(opt.lanePoints(opt.lanePoints().cuts(List.of(c))))));
 }
 static void landscapeAndHoles(){
  var mesh=closed(Structure.GROUND);var all=LaneClosureLandscape.plan(mesh,ground(false));var clip=LaneClosureLandscape.plan(mesh,ground(true));
  for(var mat:List.of(Material.SOIL,Material.CONCRETE))check(all.stream().filter(p->p.material()==mat).toList().equals(clip.stream().filter(p->p.material()==mat).toList()),"blocked foliage removed shared ordinary-road base/kerb");
  for(double d=30.25;d<89.9;d+=.5){double station=d;check(clip.stream().anyMatch(p->p.material()==Material.SOIL&&p.a().z()<=station&&p.b().z()>=station),"soil/base discontinuity");}
  check(all.stream().anyMatch(p->p.material()==Material.CONCRETE&&p.height()>1),"missing raised concrete kerb and subgrade");
  check(clip.stream().anyMatch(p->p.material()==Material.GREEN&&Math.abs(p.b().z()-49.6875)<.01),"partial foliage obstruction deletes whole metre");
  var bridge=closed(Structure.BRIDGE);var parts=RoadStructures.plan(bridge,ground(false));var raw=LaneSections.reference(bridge);
  for(double d=35;d<85;d+=5){var at=RoadStructures.sample(bridge,d);var hole=LanePoints.lane(raw,d,1);for(int side:new int[]{-1,1}){
   V point=hole.position().add(at.left().mul(side*(hole.width()/2+.16)));double station=d;
   check(parts.stream().anyMatch(p->p.material()==Material.STEEL&&Math.abs(p.a().x()-point.x())<.2&&p.a().z()<=station+.6&&p.b().z()>=station-.6),"live lane hole edge missing guardrail");
  }}
  check(LaneClosureLandscape.plan(bridge,ground(false)).isEmpty(),"bridge hole grows vegetation");cases+=3;
 }
 public static void main(String[]args){corridor();railUnion();landscapeAndHoles();System.out.println("Live423Validation: "+cases+" core scenes / "+checks+" checks; global monotone corridor, actual merge material union, interior hole rails, shared planting solids. Explicit Ground only; NO Minecraft/GPU/world claim.");}
}
