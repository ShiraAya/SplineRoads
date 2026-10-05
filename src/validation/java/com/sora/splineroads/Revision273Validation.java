package com.sora.splineroads;
import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadGeometry.*;import com.sora.splineroads.core.CorridorPlanner.*;import java.util.*;
public final class Revision273Validation {
 static int checks,plans,junctions;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static Settings road(Style s){return new Settings(Mode.STRAIGHT,s,s.defaultWidth(),1,.4,90);}
 static Node n(double x,double y,double z){return new Node(new V(x,y,z),-90,0);}
 static void corridors(){
  for(Kind kind:Kind.values())for(boolean left:new boolean[]{false,true})for(int lanes:new int[]{1,2})for(boolean reverse:new boolean[]{false,true}){
   Node[] nodes=kind==Kind.FRONTAGE?new Node[]{n(0,10,0),n(1000,10,0)}:new Node[]{n(0,10,0),n(1000,10,0),n(reverse?1000:0,2,0),n(reverse?0:1000,2,0)};
   var main=road(Style.O6_GREEN);var secondary=road(kind==Kind.FRONTAGE?Style.O2_ONE:Style.O4_YELLOW);var options=new InterchangePlanner.Options(InterchangePlanner.Preset.CLOVERLEAF,left,lanes,48,20,5,1);
   var p=CorridorPlanner.plan(nodes,main,secondary,options,new Config(kind,Sides.BOTH,Access.BOTH,kind==Kind.LAYERED?0:14,10));plans++;
   check(p.movements()==4,"common lanes do not add movements");var commons=p.legs().stream().filter(l->l.name().contains("共通段")).toList();check(commons.size()==2,"one continuous common lane per side");
   for(var common:commons){Mesh m=common.mesh();check(m.samples().stream().allMatch(s->Math.abs(s.halfWidth()*2-options.width())<.001),"common lane stays full width");
    for(Sample end:List.of(m.first(),m.last())){var peers=p.legs().stream().filter(l->l!=common&&l.mesh().settings().style().ramp()).map(InterchangePlanner.Leg::mesh).filter(a->a.first().center().distance(end.center())<1e-5||a.last().center().distance(end.center())<1e-5).toList();check(peers.size()==1,"each common end joins exactly one ramp");check(RoadQueries.contains(peers.get(0),end.center(),.01,.01),"common end is paved");}
   }
   for(var leg:p.legs())if(leg.mesh().settings().style().ramp()){
    Mesh m=leg.mesh();check(RoadGrades.maximum(m)<=.15001,"grade");check(RoadRibbon.minRadius(m)>=19.9,"radius");
    if(kind==Kind.LAYERED&&!leg.name().contains("共通段"))for(Sample s:m.samples())if(s.center().y()>3&&s.center().y()<9){double expected=Math.max(main.width(),secondary.width())/2+options.width()/2+.15;check(Math.abs(Math.abs(s.center().z())-expected)<1e-6,"zero-gap climb follows straight exterior line");}
    if(kind==Kind.FRONTAGE&&!leg.name().contains("共通段")){
     double sign=Math.signum(m.last().center().z()-m.first().center().z());for(int i=1;i<m.samples().size();i++)check((m.samples().get(i).center().z()-m.samples().get(i-1).center().z())*sign>=-1e-8,"frontage transfer never doubles back laterally");
     // After the added lane is full width, the road crosses the gap in one smooth movement.
     int last=0,changes=0;for(int i=2;i<m.samples().size()-2;i++){Sample a=m.samples().get(i-1),b=m.samples().get(i),c=m.samples().get(i+1);if(Math.min(a.halfWidth(),Math.min(b.halfWidth(),c.halfWidth()))*2<options.width()-1e-7)continue;if(Math.abs((c.center().x()-b.center().x())-(b.center().x()-a.center().x()))>1e-6)continue;double dz=c.center().z()-a.center().z();if(Math.abs(dz)<.001)continue;double bend=c.center().z()+a.center().z()-2*b.center().z();int s=Math.abs(bend)<1e-6?0:bend>0?1:-1;if(s!=0){if(last!=0&&s!=last)changes++;last=s;}}check(changes<=1,"single transfer curve, no intermediate S bends: "+leg.name()+" / "+changes);
    }
   }
  }
 }
 static void nearStraightJunctions(){
  for(double angle:new double[]{-29,-20,-10,-5,-2,-1,0,1,2,5,10,20,29})for(Style one:new Style[]{Style.O1_ONE,Style.O2_ONE,Style.O3_ONE})for(boolean incoming:new boolean[]{false,true}){
   double t=Math.toRadians(angle);V a=new V(-64,2,0),b=new V(64*Math.cos(t),2,64*Math.sin(t));var wide=new Settings(Mode.STRAIGHT,Style.O4_YELLOW,21,1,.4,90);
   var arms=List.of(JunctionSpec.arm(new Node(a,0,0),new V(1,0,0),road(one),incoming,0),JunctionSpec.arm(new Node(b,0,0),new V(-Math.cos(t),0,-Math.sin(t)),wide,false,1));
   var spec=new JunctionSpec(new V(0,2,0),JunctionSpec.Kind.INTERSECTION,false,4,12,1,4,1,JunctionSpec.Control.NONE,20,3,1,0,true,true,arms);var p=JunctionPlanner.plan(spec);junctions++;
   check(!p.pieces().isEmpty(),"near-straight mixed direction junction triangulates");check(p.boundary().stream().allMatch(v->v.distance(spec.center())<32),"bounded outer edge");check(!p.movements().isEmpty(),"valid directed movement remains");
  }
 }
 public static void main(String[] args){corridors();nearStraightJunctions();System.out.printf("Revision273 PASS %d checks / %d corridor plans / %d near-straight junctions%n",checks,plans,junctions);}
}
