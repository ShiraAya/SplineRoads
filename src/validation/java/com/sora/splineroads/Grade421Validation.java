package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
/** Production-only math. No fake clearance pass, no Minecraft/GPU claim. */
public final class Grade421Validation {
 static int checks,cases;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static String denied(Runnable f){try{f.run();}catch(IllegalArgumentException e){checks++;return e.getMessage();}throw new AssertionError("expected rejection");}
 static Settings settings(){return new Settings(Mode.CURVE,Style.C1_RAMP,4,1,.35,90);}
 static LanePoints.Options options(LanePoints.Path p,boolean override){return new LanePoints.Options(p,LanePoints.Departure.BRANCH,LanePoints.Arrival.MERGE,24,24,LanePoints.Elevation.KEEP,LanePoints.Landing.EXACT,override);}
 static V turn(V p,double angle,V origin){return new V(Math.cos(angle)*p.x()-Math.sin(angle)*p.z(),p.y(),Math.sin(angle)*p.x()+Math.cos(angle)*p.z()).add(origin);}
 static LaneRampPaths.Port port(V p,V d){return new LaneRampPaths.Port(p,d,d.left(),4,0);}
 static double grade(Mesh m){double max=0;for(int i=1;i<m.samples().size();i++){V d=m.samples().get(i).center().sub(m.samples().get(i-1).center());max=Math.max(max,Math.abs(d.y())/d.horizontalLength());}return max;}
 public static void main(String[]args){
  check(LaneRampGrade.limit(false,false)==.20&&LaneRampGrade.limit(false,true)==.25&&LaneRampGrade.limit(true,false)==.15&&LaneRampGrade.limit(true,true)==.20,"four explicit user limits");
  check(!LanePoints.Options.DEFAULT.gradeOverride(),"default override off");check(options(LanePoints.Path.DIRECT,true).withoutApproaches().gradeOverride(),"expanded path preserves override");
  for(double cap:new double[]{.15,.20,.25})for(int sign:new int[]{-1,1})for(double angle:new double[]{0,.63}){
   V origin=new V(100000,500,-100000),direction=turn(new V(1,0,0),angle,new V(0,0,0));
   var a=port(turn(new V(0,0,0),angle,origin),direction);
   double h=200*(cap-.015)/1.5*sign;var b=port(turn(new V(200,h,0),angle,origin),direction);
   var opt=options(LanePoints.Path.DIRECT,cap==.25);var candidate=LaneRampPaths.candidates(a,b,settings(),opt,cap).get(0);cases++;
   check(candidate.path()==LanePoints.Path.DIRECT,"path changed to consume extra grade");check(grade(candidate.mesh())<=cap+.000002&&grade(candidate.mesh())>cap-.025,"exact sampled cap not used");
   check(candidate.mesh().first().center().distance(a.position())<1e-7&&candidate.mesh().last().center().distance(b.position())<1e-7,"endpoints moved");
   var tooHigh=port(turn(new V(200,200*(cap+.02)/1.5*sign,0),angle,origin),direction);
   check(denied(()->LaneRampPaths.candidates(a,tooHigh,settings(),opt,cap)).contains(LaneRampGrade.label(cap)),"failure contains stale 15 percent");
   if(cap>.15)denied(()->LaneRampPaths.candidates(a,b,settings(),opt)); // legacy overload is deliberately unchanged
   var endpointSteep=new LaneRampPaths.Port(b.position(),b.direction(),b.outside(),4,cap+.01);
   denied(()->LaneRampPaths.candidates(a,endpointSteep,settings(),opt,cap));
  }
  var samples=new ArrayList<Sample>();for(int d=0;d<=120;d++)samples.add(new Sample(new V(d,100,0),new V(0,0,1),d,2));
  var base=RoadRibbon.mesh(samples,settings());
  for(boolean over:new boolean[]{false,true}){
   var obstacles=List.of(new LaneRampHeights.Constraint(50,70,4.8));
   denied(()->LaneRampHeights.solve(base,0,120,obstacles,over,.15));
   var solved=LaneRampHeights.solve(base,0,120,obstacles,over,.20);cases++;
   check(grade(solved)>.15&&grade(solved)<=.200002,"obstacle solver does not use selected cap");
   check(Math.abs(RoadStructures.sample(solved,60).center().y()-(over?104.8:95.2))<1e-7,"plateau clearance not met");
   check(solved.first().center().equals(base.first().center())&&solved.last().center().equals(base.last().center()),"solver moved mouth");
   denied(()->LaneRampHeights.adjust(base,0,120,(over?1:-1)*7));
   var adjusted=LaneRampHeights.adjust(base,0,120,(over?1:-1)*7,.20);check(grade(adjusted)>.15&&grade(adjusted)<.20,"adjust uses stale constant");
  }
  for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,0,.30})denied(()->LaneRampGrade.checked(invalid));
  leftRight();
  System.out.println("Grade421Validation: "+cases+" cases / "+checks+" checks; real endpoint, Hermite, obstacle-grade and path-identity math. NO Minecraft runtime.");
 }
 static V mirror(V p){return new V(p.x(),p.y(),-p.z());}
 static void leftRight(){
  for(double radius:new double[]{8,24,48})for(double turn:new double[]{Math.PI/4,Math.PI/2})for(int extra:new int[]{0,1,2,3}){
   V a=new V(0,100,0),dir=new V(1,0,0);V bd=new V(Math.cos(turn),0,Math.sin(turn));V b=new V(240,112,0).add(bd.mul(240));
   var o=new LanePoints.Options(LanePoints.Path.RIGHT,(extra&1)!=0?LanePoints.Departure.EXTRA:LanePoints.Departure.BRANCH,(extra&2)!=0?LanePoints.Arrival.EXTRA:LanePoints.Arrival.MERGE,radius,24,LanePoints.Elevation.KEEP,LanePoints.Landing.EXACT,false);
   var l=new LanePoints.Options(LanePoints.Path.LEFT,o.departure(),o.arrival(),radius,24,o.elevation(),o.landing(),false);
   var right=LaneRampPaths.candidates(port(a,dir),port(b,bd),settings(),o,.20).get(0);
   // Reflection includes the outside vector, preserving EXTRA lane placement as well.
   var ap=new LaneRampPaths.Port(mirror(a),mirror(dir),mirror(dir.left()),4,0);var bp=new LaneRampPaths.Port(mirror(b),mirror(bd),mirror(bd.left()),4,0);
   var left=LaneRampPaths.candidates(ap,bp,settings(),l,.20).get(0);cases++;
   check(left.path()==LanePoints.Path.LEFT&&right.path()==LanePoints.Path.RIGHT,"explicit left silently falls back to right");
   check(left.mesh().samples().size()==right.mesh().samples().size(),"mirrored route topology differs");
   for(int i=0;i<left.mesh().samples().size();i++){
    var x=left.mesh().samples().get(i);var y=right.mesh().samples().get(i);
    check(x.center().distance(mirror(y.center()))<1e-6,"actual left trajectory is not the reflected right trajectory");
    check(x.left().distance(mirror(y.left()).mul(-1))<1e-6,"left handedness corrupted");
   }
  }
  var a=port(new V(0,100,0),new V(1,0,0));var right=port(new V(8,100,8),new V(0,0,1));var left=port(mirror(right.position()),mirror(right.direction()));
  String re=denied(()->LaneRampPaths.candidates(a,right,settings(),options(LanePoints.Path.RIGHT,false),.2));
  String le=denied(()->LaneRampPaths.candidates(a,left,settings(),options(LanePoints.Path.LEFT,false),.2));
  check(re.contains("右转空间")&&le.contains("定向左转空间")&&!le.contains("右转空间"),"canonical internal right-frame error leaked into LEFT request");
 }
}
