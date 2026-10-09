package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
public final class Live443Validation {
  static int checks;
  static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
  static void closures(){
    var raw=Live435Validation.road(Style.O3_ONE,Structure.GROUND);
    for(int slot:new int[]{0,2}){
      var host=Live435Validation.cut(raw,slot,50,150,true);
      check(LaneClosureLandscape.plan(host,Live435Validation.ground(19)).isEmpty(),"exterior closed slot still filled/planted");
      check(!LaneClosureWarnings.paint(host).isEmpty(),"ground cut has no boundary paint");
      for(var cap:LaneDeck.caps(host)){
        V mid=cap.a().add(cap.b()).mul(.5);
        check(LaneClosureWarnings.paint(host).stream().flatMap(p->p.points().stream()).anyMatch(v->Math.abs(v.z()-mid.z())<.3),"missing transverse closure line");
      }
    }
    check(LaneClosureLandscape.plan(Live435Validation.cut(raw,1,50,150,true),Live435Validation.ground(19)).stream().anyMatch(p->p.material()==Material.GREEN),"internal closed lane lost planting");
  }
  static void arrows(){
    for(boolean left:new boolean[]{false,true})for(int sign:new int[]{-1,1}){
      var original=Live435Validation.road(Style.O4_RAIL,Structure.BRIDGE);
      var md=LanePoints.Data.EMPTY.additions(List.of(new LaneAdditions.Addition(new UUID(443,1),8,sign,100,32)));
      var host=LaneSections.apply(RoadRibbon.mesh(original.samples(),original.settings().options(original.settings().options().traffic(left).lanePoints(md))));
      double d=sign>0?156:60;var lane=LanePoints.lane(host,d,8);
      var paints=RoadJunction.arrows(host,List.of());
      check(paints.stream().flatMap(p->p.points().stream()).anyMatch(v->v.distance(lane.position())<4),"added slot has no direction arrow left="+left+" sign="+sign);
    }
  }
  static void sharedHeight(){
    var settings=new Settings(Mode.CURVE,Style.C1_RAMP,4,1,.35,90);
    var parent=new ArrayList<Sample>();var child=new ArrayList<Sample>();
    for(int i=0;i<=240;i++){
      double d=i*.5,x=.003*d*d,y=20+.08*d;
      parent.add(new Sample(new V(0,20,d),new V(1,0,0),d,2));
      V tangent=new V(.006*d,0,1).horizontalUnit();
      child.add(new Sample(new V(x,y,d),tangent.left(),d,2));
    }
    var host=RoadRibbon.mesh(parent,settings);var raw=RoadRibbon.mesh(child,settings);
    var fitted=LaneRampThroat.fit(raw,List.of(host),true);int end=LaneRampThroat.end(fitted,List.of(host),true);
    check(end>10,"fixture has no real shared throat");
    for(int i=0;i<=end;i++)check(Math.abs(fitted.samples().get(i).center().y()-20)<1e-7,"normal fork has stacked shared slabs");
    check(fitted.last().center().equals(raw.last().center()),"throat fit moved far endpoint");
    check(fitted.samples().get(end+30).center().y()>20,"height failed to diverge after fork");
  }
  static void collision(){
    var road=Live435Validation.road(Style.C1_RAMP,Structure.BRIDGE);
    var p=new Part(new V(0,0,100),new V(0,0,100),1.5,40,true,Material.CONCRETE);
    check(RoadClearance.structureInvades(p,road,4.25),"pier crossing lower live ramp accepted");
    check(!RoadClearance.structureInvades(new Part(new V(12,0,100),new V(12,0,100),1.5,40,true,Material.CONCRETE),road,4.25),"unrelated support rejected");
  }
  public static void main(String[]args){closures();arrows();sharedHeight();collision();System.out.println("Live443Validation "+checks+" checks PASS");}
}
