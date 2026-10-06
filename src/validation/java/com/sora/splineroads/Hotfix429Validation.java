package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
public final class Hotfix429Validation {
 static int checks;
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static void main(String[]args){
  var s=new Settings(Mode.STRAIGHT,Style.C1_RAMP,4,1,.35,90);var points=new ArrayList<Sample>();
  for(int i=0;i<=300;i++)points.add(new Sample(new V(0,10,i),new V(-1,0,0),i,2));var m=RoadRibbon.mesh(points,s);
  var bounds=List.of(new LaneRampCorridor.Bound(45,65,3,true),new LaneRampCorridor.Bound(235,255,3,false));
  var out=LaneRampCorridor.solveMixed(m,0,300,bounds,.2);
  check(out.first().center().distance(m.first().center())<1e-8&&out.last().center().distance(m.last().center())<1e-8,"fixed ports moved");
  for(var at:out.samples()){
   if(at.distance()>=45&&at.distance()<=65)check(at.center().y()>=13-1e-7,"low crossing not cleared");
   if(at.distance()>=235&&at.distance()<=255)check(at.center().y()<=7+1e-7,"high crossing not cleared");
  }
  LaneRampGrade.validate(out,.2);check(true,"local grade");
  boolean rejected=false;try{LaneRampCorridor.solveMixed(m,0,300,List.of(new LaneRampCorridor.Bound(0,1,3,true)),.2);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"fixed collision was disabled");
  System.out.println("Hotfix429Validation: "+checks+" checks PASS; mixed over/under retains fixed endpoints and final grade checks; pure geometry only");
 }
}
