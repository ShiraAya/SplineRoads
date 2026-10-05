package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
public final class Hotfix351Validation {
 static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static List<V> lines(Mesh m,Sample s){var layout=RoadProfile.layout(m,s);return layout.dividers().stream().filter(d->d>layout.motorMin()+.001&&d<layout.motorMax()-.001).map(d->s.at(d,0)).toList();}
 static void seam(Mesh stem,Mesh branch,Sample end,int side){
  var expected=lines(stem,stem.last()).stream().filter(p->p.sub(stem.last().center()).dot(stem.last().left())*side>0).toList();var actual=lines(branch,end);
  check(expected.size()==actual.size(),"same divider count at throat");
  for(var p:expected)check(actual.stream().anyMatch(q->p.distance(q)<1e-7),"divider must meet A at exact world coordinate "+p+" / "+actual);
 }
 public static void main(String[] args){
  int cases=0;
  for(boolean left:new boolean[]{false,true})for(Style style:List.of(Style.O2_YELLOW,Style.O4_GREEN,Style.O6_YELLOW,Style.H8_RAIL))for(boolean extras:new boolean[]{false,true})for(boolean unequal:new boolean[]{false,true}){
   int side=left?-1:1;var main=Revision34Validation.road(style);main=main.options(main.options().traffic(left).extras(extras,false,extras));main=new Settings(Mode.CURVE,style,RoadProfile.width(style,main.options(),3.5),1,.4,90).options(main.options());
   var target=RoadProfile.choose(RoadProfile.catalog(style).type(),RoadProfile.catalog(style).lanes()/2,false,RoadProfile.Median.NONE,RoadProfile.catalog(style).shoulder());
   var out=Revision34Validation.road(unequal?Style.O1_ONE:target);var in=Revision34Validation.road(unequal?Style.O4_ONE:target);
   var plan=YJunctionPlanner.plan(new Node(new V(0,12,0),180,0),new Node(new V(side*80,12,-200),180,0),new Node(new V(-side*80,12,-200),0,0),main,out,in,.4);
   seam(plan.stem(),plan.outbound(),plan.outbound().first(),side);seam(plan.stem(),plan.inbound(),plan.inbound().last(),-side);
   for(double at=0.13;at<12;at+=.19){
    check(RoadSurface.painted(plan.outbound(),at)==RoadSurface.painted(plan.stem(),plan.stem().length()+at),"outbound dash phase follows stem");
    check(RoadSurface.painted(plan.inbound(),plan.inbound().length()-at)==RoadSurface.painted(plan.stem(),plan.stem().length()+at),"inbound reversed dash phase follows stem");
   }
   for(var mesh:List.of(plan.stem(),plan.outbound(),plan.inbound()))for(var face:RoadSurface.build(mesh,List.of(),List.of()).markings())for(var point:face.points())check(Double.isFinite(point.x()+point.y()+point.z()),"finite rendered paint");
   cases++;
  }
  System.out.println("Hotfix351 PASS "+checks+" checks in "+cases+" Y cases; exact lane positions and reversed dash continuity");
 }
}
