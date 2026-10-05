package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import com.sora.splineroads.core.CorridorPlanner.*;
import java.util.*;
public final class Revision272Validation {
  static int checks,plans,openings;
  static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
  static Settings road(Style s){return new Settings(Mode.STRAIGHT,s,s.defaultWidth(),1,.4,90);}
  static Node n(double x,double y,double z){return new Node(new V(x,y,z),-90,0);}
  static void openings(){
    for(Kind kind:Kind.values())for(Style style:new Style[]{Style.O2_YELLOW,Style.O6_GREEN,Style.H6_RAIL})for(boolean left:new boolean[]{false,true})for(int lanes:new int[]{1,2}){
      var nodes=kind==Kind.FRONTAGE?new Node[]{n(0,10,0),n(1000,10,0)}:new Node[]{n(0,10,0),n(1000,10,0),n(1000,2,0),n(0,2,0)};
      var p=CorridorPlanner.plan(nodes,road(style),road(kind==Kind.FRONTAGE?Style.O2_ONE:Style.O4_YELLOW),new Options(Preset.CLOVERLEAF,left,lanes,48,20,5,1),new Config(kind,Sides.BOTH,Access.BOTH,14,kind==Kind.FRONTAGE?10:2));plans++;
      var hosts=p.legs().stream().map(Leg::mesh).filter(m->!m.settings().style().ramp()).toList();
      for(var l:p.legs())if(l.mesh().settings().style().ramp()){
        Mesh ramp=l.mesh();check(RoadRibbon.minRadius(ramp)>=19.9,"radius");check(RoadGrades.maximum(ramp)<=.15001,"grade");
        for(boolean first:new boolean[]{true,false}){
          Sample end=first?ramp.first():ramp.last();Mesh host=hosts.stream().filter(h->RoadQueries.contains(h,end.center(),end.halfWidth()-.1,.1)).findFirst().orElseThrow(()->new AssertionError("ramp endpoint is not on a host"));
          double longest=0,run=0,middle=0;
          for(double d=0;d<Math.min(ramp.length()/2,160);d+=.5){
            Sample at=RoadStructures.sample(ramp,first?d:ramp.length()-d);var q=RoadQueries.horizontal(host,at.center());
            double overlap=q.sample().halfWidth()+at.halfWidth()-q.horizontalDistance();
            if(at.halfWidth()*2>=ramp.settings().width()*.999&&Math.abs(at.center().y()-q.sample().center().y())<.01&&overlap>.6&&q.horizontalDistance()>q.sample().halfWidth()+at.halfWidth()-1.2){run+=.5;if(run>longest){longest=run;middle=first?d-run/2:ramp.length()-d+run/2;}}
            else run=0;
          }
          check(longest>=7.5,"full-width exterior lane must share host edge for a usable opening, got "+longest);openings++;
          Sample at=RoadStructures.sample(ramp,middle);var q=RoadQueries.horizontal(host,at.center());int side=q.lateral()>0?1:-1;
          V edge=q.sample().at(side*q.sample().halfWidth(),0);
          check(RoadQueries.joins(host,ramp,edge),"host guardrail opening");
          V inner=at.at(-Math.signum(at.center().sub(q.sample().center()).dot(at.left()))*(at.halfWidth()-.25),0);
          check(RoadQueries.joins(ramp,host,inner),"ramp inner guardrail opening");
          for(double t=0;t<=1;t+=.05)check(RoadQueries.contains(host,edge.add(at.center().sub(edge).mul(t)),0,.12)||RoadQueries.contains(ramp,edge.add(at.center().sub(edge).mul(t)),0,.12),"continuous paved access");
          check(RoadJunction.dividerZones(ramp,List.of(host)).stream().noneMatch(z->z.contains(at.distance())),"complete added lane must not remain a taper exclusion zone");
        }
      }
    }
  }
  static void oneEnd(){
    for(Kind kind:Kind.values())for(Adjustment mode:new Adjustment[]{Adjustment.START,Adjustment.END})for(boolean reverse:new boolean[]{false,true}){
      Node[] nodes=kind==Kind.FRONTAGE?new Node[]{n(0,10,0),n(80,10,0)}:new Node[]{n(0,10,0),n(80,10,0),n(reverse?80:0,2,0),n(reverse?0:80,2,0)};
      var main=road(Style.O6_GREEN);var aux=road(kind==Kind.FRONTAGE?Style.O2_ONE:Style.O4_YELLOW);var o=new Options(Preset.CLOVERLEAF,false,2,48,20,5,1).adjust(true);
      var c=new Config(kind,Sides.BOTH,Access.BOTH,14,2,mode);var plan=CorridorPlanner.plan(nodes,main,aux,o,c);plans++;
      int fixed=mode==Adjustment.START?1:0;check(plan.anchors().get(fixed).equals(nodes[fixed]),"fixed AB end changes");
      if(kind==Kind.LAYERED){int f=reverse?3-fixed:2+fixed;check(plan.anchors().get(f).equals(nodes[f]),"fixed CD physical side changes");}
      var again=CorridorPlanner.plan(plan.anchors().toArray(Node[]::new),main,aux,o,c);check(again.anchors().equals(plan.anchors()),"fit repeats on save");
    }
  }
  public static void main(String[]args){openings();oneEnd();System.out.printf("Revision272 PASS %d checks / %d plans / %d full-width road-ramp openings%n",checks,plans,openings);}
}
