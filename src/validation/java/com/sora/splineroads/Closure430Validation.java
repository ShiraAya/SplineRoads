package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
public final class Closure430Validation {
 public static void main(String[] args)throws Exception{
  var clip=LaneClosureLandscape.class.getDeclaredMethod("addUnblocked",List.class,Part.class,Ground.class,int.class);clip.setAccessible(true);int checks=0;
  Ground ground=new Ground(){public double top(double x,double z,double y){return 0;}public boolean joined(V p){return false;}public boolean blocked(Part p){return Math.max(p.a().x(),p.b().x())>.75&&Math.min(p.a().x(),p.b().x())<1.25;}};
  for(int frames=0;frames<4;frames++){
   var a=new V(0,0,0);var b=new V(2,0,0);var side=b.sub(a).horizontalUnit().left().mul(.1);
   var part=new Part(a,b,.2,1,false,Material.CONCRETE,(frames&1)==0?null:side,(frames&2)==0?null:side,"clip-test");
   var out=new ArrayList<Part>();clip.invoke(null,out,part,ground,0);
   if(out.isEmpty())throw new AssertionError("clipping lost all unobstructed end kerb");checks++;
   double length=0;for(var p:out){if(ground.blocked(p)||!p.model().equals(part.model())||p.material()!=part.material()||p.width()!=part.width()||p.height()!=part.height())throw new AssertionError("clip changed prism or retained conflict");length+=p.a().distance(p.b());checks++;}
   if(Math.abs(length-1.5)>1e-8)throw new AssertionError("end-kerb clipping must preserve both safe sections");checks++;
  }
  System.out.println("Closure430Validation: "+checks+" checks PASS; null/partial/explicit end-kerb frames safely clipped with exact remaining length; production geometry only.");
 }
}
