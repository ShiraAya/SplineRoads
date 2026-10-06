package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Actual geometry tests, no original screenshot world / Minecraft GPU claims. */
public final class SR15Geometry426Validation {
  static int cases,checks;
  static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
  static void near(double a,double b,String s){check(Math.abs(a-b)<1e-6,s+": "+a+" / "+b);}
  static Ground ground(boolean joined){return new Ground(){public double top(double x,double z,double y){return 0;}public boolean joined(V p){return joined;}public boolean blocked(Part p){return false;}};}
  static Mesh closed(boolean left,boolean bridge,boolean curve){
    var o=RoadProfile.Options.DEFAULT.traffic(left);var s=new Settings(curve?Mode.CURVE:Mode.STRAIGHT,Style.O6_YELLOW,RoadProfile.width(Style.O6_YELLOW,o,4),1,.35,90).options(o).structure(bridge?Structure.BRIDGE:Structure.GROUND);
    var raw=RoadGeometry.build(new Node(new V(100000,20,-100000),0,0),new Node(new V(100000+(curve?30:0),20,-99600),curve?15:0,0),s);
    var cut=new LaneSections.Cut(new UUID(426,1),4,1,100,240,32,null,true,false,true);
    return LaneSections.apply(RoadRibbon.mesh(raw.samples(),s.options(o.lanePoints(LanePoints.Data.EMPTY.cuts(List.of(cut))))));
  }
  static void ends(){
    for(boolean left:new boolean[]{false,true})for(boolean curve:new boolean[]{false,true}){
      cases++;var m=closed(left,false,curve);var parts=LaneClosureLandscape.plan(m,ground(false));
      check(parts.size()==702,"140 m of five-part planter plus two end kerbs");
      for(double d:new double[]{100.1,239.9}){
        var at=LanePoints.lane(LaneSections.reference(m),d,4);
        check(parts.stream().anyMatch(p->p.material()==Material.CONCRETE&&p.a().sub(p.b()).horizontalLength()>3&&p.a().add(p.b()).mul(.5).sub(at.position()).horizontalLength()<1e-5&&p.height()>1),"end kerb missing at "+d);
      }
      var bridge=closed(left,true,curve);var rail=RoadStructures.plan(bridge,ground(false));
      for(var cap:LaneDeck.caps(bridge)){
        V mid=cap.a().add(cap.b()).mul(.5),axis=cap.b().sub(cap.a()).horizontalUnit();
        check(rail.stream().anyMatch(p->p.material()==Material.STEEL&&Math.abs(p.b().sub(p.a()).horizontalUnit().dot(axis))>.999&&p.a().add(p.b()).mul(.5).sub(mid).horizontalLength()<.6),"transverse rail missing");
      }
      var joined=RoadStructures.plan(bridge,ground(true));
      check(joined.stream().noneMatch(p->p.material()==Material.STEEL),"same-height valid joining deck gained blocking rails");
      var warnings=LaneClosureWarnings.paint(bridge);check(warnings.size()==6,"three Xs required on real approach");
      int sign=LanePoints.lane(LaneSections.reference(bridge),150,4).sign();double boundary=sign>0?100:240;
      for(var w:warnings)for(V v:w.points()){
        var q=RoadQueries.horizontal(LaneSections.reference(bridge),v);double lead=sign*(boundary-q.sample().distance());
        check(lead>5&&lead<27,"X on wrong side of closure");check(RoadQueries.contains(bridge,v,-.04,.1),"X not on existing pavement");
      }
      var raw=LaneSections.reference(bridge);var restored=RoadRibbon.mesh(raw.samples(),raw.settings().options(raw.settings().options().lanePoints(LanePoints.Data.EMPTY)));
      check(LaneClosureWarnings.paint(restored).isEmpty(),"deleted closure retains warnings");
    }
  }
  static Mesh stairs(double sign,double yaw,double origin){
    var ss=new ArrayList<Sample>();V along=new V(Math.sin(yaw),0,Math.cos(yaw));
    for(int i=0;i<=320;i++){
      double y=i<40?i*.16:i<110?6.4:i<160?6.4+(i-110)*.1:i<220?11.4:11.4+(i-220)*.086;
      ss.add(new Sample(new V(origin,40,origin).add(along.mul(i)).add(new V(0,sign*y,0)),along.left(),i,2));
    }
    return RoadRibbon.mesh(ss,new Settings(Mode.STRAIGHT,Style.C1_RAMP,4,1,.35,90));
  }
  static void corridor(){
    for(double sign:new double[]{-1,1})for(double yaw:new double[]{0,.6})for(double origin:new double[]{0,100000}){
      cases++;var base=stairs(sign,yaw,origin);
      var m=LaneRampCorridor.solve(base,0,base.length(),List.of(new LaneRampHeights.Constraint(65,78,.5)),sign>0,.2);
      double total=0;int flat=0;
      for(int i=1;i<m.samples().size();i++){
        double dy=m.samples().get(i).center().y()-m.samples().get(i-1).center().y();total+=Math.abs(dy);
        check(sign*dy>=-1e-6,"spurious reverse slope");check(Math.abs(dy)<=.2000001,"grade cap exceeded");
        if(i>90&&i<300&&Math.abs(dy)<.0001)flat++;
      }
      near(total,20,"avoidable vertical travel");check(flat==0,"old stepped plateau retained: "+flat);
      near(m.samples().get(1).center().y(),base.samples().get(1).center().y(),"source signed tangent changed");
      near(m.samples().get(m.samples().size()-2).center().y(),base.samples().get(base.samples().size()-2).center().y(),"destination signed tangent changed");
      for(int i=65;i<=78;i++)check(sign*(m.samples().get(i).center().y()-base.samples().get(i).center().y())>=.5-1e-6,"real crossing clearance lost");
    }
    cases++;var base=stairs(1,0,0);boolean rejected=false;
    try{LaneRampCorridor.solve(base,0,base.length(),List.of(new LaneRampHeights.Constraint(319,320,8)),true,.2);}catch(IllegalArgumentException e){
      rejected=true;check(e.getMessage().contains("X=")&&e.getMessage().contains("Y=")&&e.getMessage().contains("距 B"),"failure lacks a usable position");
      check(e.getMessage().contains("不是输入坡比超限"),"failure mislabels legal input slope");
    }check(rejected,"fixed port collision bypassed");
  }
  public static void main(String[]args){ends();corridor();System.out.println("SR15Geometry426Validation: "+cases+" cases / "+checks+" checks PASS; planter end kerbs, transverse exposed-end rails, approach X warnings, whole-span profile and diagnostic positions. Core/Ground only, NOT original world/GPU.");}
}
