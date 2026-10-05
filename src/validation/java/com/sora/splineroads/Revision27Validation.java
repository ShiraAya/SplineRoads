package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import com.sora.splineroads.core.CorridorPlanner.*;
import java.util.*;

public final class Revision27Validation {
  static int checks,plans,arrows;
  static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  static Settings settings(Style style){return new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.4,90);}
  static Node[] nodes(Kind kind,boolean reverse){
    Node a=new Node(new V(0,10,0),0,0),b=new Node(new V(900,10,0),0,0),c=new Node(new V(0,2,0),0,0),d=new Node(new V(900,2,0),0,0);
    return kind==Kind.FRONTAGE?new Node[]{a,b}:reverse?new Node[]{a,b,d,c}:new Node[]{a,b,c,d};
  }
  static void corridors(){
    for(Kind kind:Kind.values())for(boolean left:new boolean[]{false,true})for(int lanes:new int[]{1,2})
      for(Sides sides:Sides.values())for(Access access:Access.values()){
        var c=new Config(kind,sides,access,14,2);var o=new Options(Preset.CLOVERLEAF,left,lanes,48,20,5,1);
        var p=CorridorPlanner.plan(nodes(kind,left),settings(Style.H4_RAIL),settings(kind==Kind.FRONTAGE?Style.O2_ONE:Style.O4_YELLOW),o,c);
        int per=access==Access.BOTH?2:access==Access.NONE?0:1;
        check(p.movements()==per*(sides==Sides.BOTH?2:1),"requested access count");
        int aux=0;
        for(var leg:p.legs()){
          Mesh m=leg.mesh();check(RoadGrades.maximum(m)<=.15001,"corridor grade limit");
          if(leg.name().contains("辅路")){aux++;check(!RoadProfile.catalog(m.settings().style()).twoWay(),"frontage is same-direction one-way");}
          if(!m.settings().style().ramp())continue;
          int sign=leg.from();check(sign==leg.to(),"same-side movement only");
          check(RoadRibbon.minRadius(m)+.1>=o.radius(),"ramp radius");
          int flow=sign*RoadProfile.trafficSign(left);
          check((m.last().center().x()-m.first().center().x())*flow>0,"no reverse direction connector");
          for(Sample s:m.samples()){
            check(s.center().z()*sign>0,"no median crossing or opposite-side movement");
            check(s.center().y()>=1.999&&s.center().y()<=10.001,"no unnecessary extra level");
          }
        }
        if(kind==Kind.FRONTAGE)check(aux==(sides==Sides.BOTH?2:1),"requested frontage sides");
        plans++;
      }
    boolean shortRejected=false;
    try{CorridorPlanner.plan(new Node[]{new Node(new V(0,10,0),0,0),new Node(new V(80,10,0),0,0)},settings(Style.H4_RAIL),settings(Style.O2_ONE),new Options(Preset.CLOVERLEAF,false,1,48,20,5,1),new Config(Kind.FRONTAGE,Sides.BOTH,Access.BOTH,12,2));}
    catch(IllegalArgumentException e){shortRejected=e.getMessage().contains("至少需");}
    check(shortRejected,"too-short corridors reject before world writes");
  }
  static void mergeArrows(){
    for(Style style:new Style[]{Style.O6_GREEN,Style.H6_RAIL})for(int n:new int[]{5,6})for(boolean left:new boolean[]{false,true}){
      var p=Revision26Validation.plan(n,2,left,style,false);var all=p.legs().stream().map(l->RoadRenderMesh.simplify(l.mesh())).toList();
      for(Mesh m:all)if(m.settings().style().ramp()&&m.last().halfWidth()<1.6){
        var peers=all.stream().filter(a->a!=m).toList();var paint=RoadJunction.arrows(m,peers);
        check(paint.size()==6&&paint.stream().filter(f->f.points().size()==3).count()==2,"both complete arrows survive closure filtering");
        for(int lane=0;lane<2;lane++){
          // The emitter keeps each complete arrow together. Neither head nor shaft may cross its lane.
          for(var face:paint.subList(lane*3,lane*3+3))for(V v:face.points()){
            var q=RoadQueries.horizontal(m,v);var l=RoadProfile.layout(m,q.sample());
            check(q.lateral()>=l.motorMin()+lane*l.laneWidth()+.17&&q.lateral()<=l.motorMin()+(lane+1)*l.laneWidth()-.17,"whole arrow stays inside its lane");
          }
        }
        // Test real clipped surface at both arrowheads, rather than just the unclipped shapes.
        var surface=RoadSurface.build(m,all.stream().filter(a->!a.settings().style().ramp()).toList(),peers);
        for(var head:paint)if(head.points().size()==3){V center=head.points().get(0).add(head.points().get(1)).add(head.points().get(2)).mul(1.0/3);check(Revision231Validation.painted(surface.markings(),center),"arrowhead survives surface clipping");}
        Revision231Validation.grids.clear();arrows+=2;
      }
    }
  }
  public static void main(String[] args){corridors();mergeArrows();System.out.printf("Revision27 PASS %d checks / %d corridor plans / %d complete merge arrows%n",checks,plans,arrows);}
}
