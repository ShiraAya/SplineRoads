package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** New P1 regressions against real production math. No Minecraft-world/GPU claim. */
public final class P1Review424Validation {
  static int cases,checks;
  static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
  static void near(double a,double b,double tolerance,String message){check(Math.abs(a-b)<=tolerance,message+": "+a+" / "+b);}
  static String reject(Runnable run){try{run.run();}catch(IllegalArgumentException e){checks++;return e.getMessage();}throw new AssertionError("expected actual infeasibility");}
  static Settings settings(){return new Settings(Mode.STRAIGHT,Style.C1_RAMP,4,1,.35,90);}
  static void profiles(){
    for(double length:new double[]{32,64,307,1000})for(double rise:new double[]{.25,2,8})
    for(double cap:new double[]{.15,.20,.25})for(int sign:new int[]{-1,1})
    for(double first:new double[]{0,.05,cap})for(double last:new double[]{0,.05,cap}){
      if(rise>=length*cap)continue;cases++;
      double y=100,travel=0;
      for(int i=1;i<=2000;i++){
        double d=length*i/2000.,next=LaneRampProfile.height(100,100+sign*rise,sign*first,sign*last,length,d,cap);
        check(sign*(next-y)>=-1e-9,"compatible endpoint slopes introduce an unnecessary reversal");
        check(Math.abs(next-y)/(length/2000.)<=cap+1e-7,"grade fit exceeds selected cap");
        check(sign*(next-100)>=-1e-7&&sign*(100+sign*rise-next)>=-1e-7,"overshoot before final merge");
        travel+=Math.abs(next-y);y=next;
      }
      near(y,100+sign*rise,1e-9,"B height moved");near(travel,rise,1e-7,"extra vertical travel");
      double eps=1e-6;
      near((LaneRampProfile.height(100,100+sign*rise,sign*first,sign*last,length,eps,cap)-100)/eps,sign*first,1e-5,"A tangent flattened");
      near((100+sign*rise-LaneRampProfile.height(100,100+sign*rise,sign*first,sign*last,length,length-eps,cap))/eps,sign*last,1e-5,"B tangent flattened");
    }
    for(double cap:new double[]{.15,.20,.25})for(int sign:new int[]{-1,1}){
      cases++;double length=200,rise=length*cap*.90;
      var a=new LaneRampPaths.Port(new V(0,100,0),new V(1,0,0),new V(0,0,1),4,0);
      var b=new LaneRampPaths.Port(new V(length,100+sign*rise,0),a.direction(),a.outside(),4,0);
      var opt=new LanePoints.Options(LanePoints.Path.DIRECT,LanePoints.Departure.BRANCH,LanePoints.Arrival.FLOW,24,24,LanePoints.Elevation.KEEP,LanePoints.Landing.EXACT,false);
      var mesh=LaneRampPaths.candidates(a,b,settings(),opt,cap).get(0).mesh();LaneRampGrade.validate(mesh,cap);
      var report=LaneRampGrade.report(mesh,0,mesh.length(),cap);
      near(report.horizontal(),length,1e-6,"horizontal rather than 3-D grade budget");
      near(report.minimum(),length*.90,1e-6,"minimum travel/run diagnostic");
      check(report.maximum()<=cap+1e-6,"final maximum grade exceeds cap");
      reject(()->LaneRampProfile.height(100,100+sign*length*(cap+.001),0,0,length,length/2,cap));
    }
  }
  static Mesh base(boolean oldCrest){
    var samples=new ArrayList<Sample>();
    for(int i=0;i<=307;i++){
      double t=i/307.,y=8*t*t*(3-2*t);
      if(oldCrest&&i>100&&i<240)y+=7*Math.pow(Math.sin(Math.PI*(i-100)/140.),2);
      samples.add(new Sample(new V(i,y,0),new V(0,0,1),i,2));
    }
    return RoadRibbon.mesh(samples,settings());
  }
  static void corridors(){
    var base=base(true);var constraints=List.of(new LaneRampHeights.Constraint(30,50,.5));
    var solved=LaneRampCorridor.solve(base,0,base.length(),constraints,true,.2);cases++;
    double travel=0;
    for(int i=1;i<solved.samples().size();i++){
      double dy=solved.samples().get(i).center().y()-solved.samples().get(i-1).center().y();
      check(dy>=-1e-7,"old non-obstacle crest remains a hard bound");travel+=Math.abs(dy);
    }
    near(travel,8,1e-6,"unnecessary old crest not removed");LaneRampGrade.validate(solved,.2);
    // Previously clear contact with zero additional lift must remain an actual bound.
    var clear=LaneRampCorridor.solve(base,0,base.length(),List.of(new LaneRampHeights.Constraint(165,185,0)),true,.2);cases++;
    for(int i=0;i<base.samples().size();i++)if(base.samples().get(i).distance()>=165&&base.samples().get(i).distance()<=185)
      check(clear.samples().get(i).center().y()>=base.samples().get(i).center().y()-1e-6,"zero-lift crossing bound was discarded");
    var fixed=base(false);double lock=32;
    var end=LaneRampCorridor.solve(fixed,lock,fixed.length()-lock,List.of(new LaneRampHeights.Constraint(100,120,.5)),true,.2);cases++;
    for(int i=0;i<fixed.samples().size();i++)if(fixed.samples().get(i).distance()<=lock||fixed.samples().get(i).distance()>=fixed.length()-lock)
      near(fixed.samples().get(i).center().y(),end.samples().get(i).center().y(),1e-9,"fixed terminal approach changed");
    String error=reject(()->LaneRampCorridor.solve(fixed,0,fixed.length(),List.of(new LaneRampHeights.Constraint(fixed.length()-1,fixed.length(),8)),true,.2));
    check(error.contains("总水平路径")&&error.contains("可布坡")&&error.contains("冲突站位"),"diagnostic loses whole-path/fixed-contact budget");
  }
  static LaneSections.Cut cut(int number,int lane,double a,double b){return new LaneSections.Cut(new UUID(424,number),lane,1,a,b,32,null,true,true,true);}
  static Mesh closed(List<LaneSections.Cut> cuts){
    var raw=RoadGeometry.build(new Node(new V(0,10,0),0,0),new Node(new V(0,10,120),0,0),new Settings(Mode.STRAIGHT,Style.O3_ONE,13,1,.35,90).structure(Structure.GROUND));
    var opt=raw.settings().options();return LaneSections.apply(RoadRibbon.mesh(raw.samples(),raw.settings().options(opt.lanePoints(opt.lanePoints().cuts(cuts)))));
  }
  static final Ground GROUND=new Ground(){public double top(double x,double z,double y){return y-1;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
  static void planting(){
    var original=LaneClosureLandscape.plan(closed(List.of(cut(1,1,20,90))),GROUND);
    for(var cuts:List.of(List.of(cut(2,1,20,60),cut(3,1,50,90)),List.of(cut(4,1,20,90),cut(5,1,20,90)),List.of(cut(6,1,55,90),cut(7,1,20,55)))){
      cases++;var union=LaneClosureLandscape.plan(closed(cuts),GROUND);
      check(original.equals(union),"overlapping/adjacent/reordered reservations duplicate or break native planting");
      check(new HashSet<>(union).size()==union.size(),"duplicate physical planter component");
    }
    var restored=LaneClosureLandscape.plan(closed(List.of()),GROUND);check(restored.isEmpty(),"deletion leaves planting behind");
    cases++;check(original.size()==350,"expected 70 m of complete five-component native bed");
    for(int i=0;i<original.size();i+=5){
      var run=original.subList(i,i+5);
      check(run.stream().filter(p->p.material()==Material.SOIL).count()==1,"missing foundation");
      check(run.stream().filter(p->p.material()==Material.GREEN).count()==2,"missing native leaf layers");
      check(run.stream().filter(p->p.material()==Material.CONCRETE).count()==2,"missing both curbs");
    }
  }
  public static void main(String[] args){profiles();corridors();planting();System.out.println("P1Review424Validation: "+cases+" scenes / "+checks+" checks PASS. Actual core geometry; NOT Minecraft world transactions/GPU acceptance.");}
}
