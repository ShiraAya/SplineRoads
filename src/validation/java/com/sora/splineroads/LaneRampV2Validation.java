package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
public final class LaneRampV2Validation {
 static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static LaneRampPaths.Port port(double x,double y,double z,double dx,double dz){V d=new V(dx,0,dz);return new LaneRampPaths.Port(new V(x,y,z),d,d.left(),5,0);}
 static Mesh plan(LaneRampPaths.Port a,LaneRampPaths.Port b,LanePoints.Path p,boolean ae,boolean be){return LaneRampPaths.candidates(a,b,new Settings(Mode.CURVE,Style.O1_ONE,5,1,.35,90),new LanePoints.Options(p,ae,be,24,32)).get(0).mesh();}
 static void checkEnds(Mesh m,LaneRampPaths.Port a,LaneRampPaths.Port b){check(m.first().center().distance(a.position())<1e-6,"source lane retained");check(m.last().center().distance(b.position())<1e-6,"target lane retained");check(m.first().left().left().mul(-1).dot(a.direction())>.99999,"source direction");check(m.last().left().left().mul(-1).dot(b.direction())>.99999,"target direction");for(int i=1;i<m.samples().size();i++){V d=m.samples().get(i).center().sub(m.samples().get(i-1).center());check(Math.abs(d.y())/d.horizontalLength()<=.15001,"15% grade");}RoadRibbon.checkSelfIntersections(m,4);check(!RoadRaster.raster(m).isEmpty(),"real collision raster");}
 public static void main(String[] args){var a=port(0,100,0,0,-1);var direct=port(16,108,-240,0,-1);for(boolean ae:new boolean[]{false,true})for(boolean be:new boolean[]{false,true})checkEnds(plan(a,direct,LanePoints.Path.DIRECT,ae,be),a,direct);
  boolean shortExtra=false;try{plan(a,port(16,108,-180,0,-1),LanePoints.Path.DIRECT,true,true);}catch(IllegalArgumentException e){shortExtra=true;}check(shortExtra,"full parallel approaches cannot consume needed grade transition");
  var right=port(100,100,-100,1,0);checkEnds(plan(a,right,LanePoints.Path.RIGHT,false,false),a,right);
  var left=port(-100,100,-100,-1,0);var loop=plan(a,left,LanePoints.Path.LEFT_LOOP,false,false);checkEnds(loop,a,left);check(loop.length()>a.position().distance(left.position())+100,"left uses actual long loop");var auto=plan(a,left,LanePoints.Path.AUTO,false,false);checkEnds(auto,a,left);
  boolean fail=false;try{plan(a,right,LanePoints.Path.DIRECT,false,false);}catch(IllegalArgumentException e){fail=true;}check(fail,"direct does not impersonate a ninety-degree turn");
  fail=false;try{plan(a,port(0,150,-20,0,-1),LanePoints.Path.DIRECT,false,false);}catch(IllegalArgumentException e){fail=true;}check(fail,"unachievable grade rejected");
  var road=RoadGeometry.build(new Node(new V(0,0,0),0,0),new Node(new V(0,0,100),0,0),new Settings(Mode.STRAIGHT,Style.O4_YELLOW,17,1,.35,90));check(LanePoints.lane(road,50,0).direction().dot(LanePoints.lane(road,50,2).direction())<-.99,"opposite carriageways");check(LanePoints.lane(road,50,0).position().distance(LanePoints.lane(road,50,1).position())>3,"specific lane positions");
  System.out.println("LaneRampV2Validation: "+checks+" checks passed");}
}
