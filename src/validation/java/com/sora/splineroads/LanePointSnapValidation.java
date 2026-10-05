package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Regression for curved legacy roads making every world transaction fail to stabilize. */
public final class LanePointSnapValidation {
  static int checks;
  static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
  public static void main(String[] args){
    double legacyDrift=0;
    for(Style style:List.of(Style.O6_RAIL,Style.O4_YELLOW,Style.O3_ONE,Style.H6_RAIL)){
      var settings=new Settings(Mode.CURVE,style,RoadProfile.width(style,RoadProfile.Options.DEFAULT,4),1,.4,90);
      var mesh=RoadGeometry.build(new Node(new V(.5,2,.5),0,0),new Node(new V(100.5,2,180.5),-90,0),settings);
      for(double fraction:new double[]{0,.25,.6,1})for(int lane=0;lane<RoadProfile.catalog(style).lanes();lane++){
        var selected=LanePoints.lane(mesh,mesh.length()*fraction,lane);
        legacyDrift=Math.max(legacyDrift,selected.position().distance(LanePoints.lane(mesh,RoadQueries.horizontal(mesh,selected.position()).sample().distance(),lane).position()));
        var origin=fraction==0?LanePoints.Origin.AUTOMATIC_START:fraction==1?LanePoints.Origin.AUTOMATIC_END:LanePoints.Origin.MANUAL;
        var p=LanePoints.point(UUID.randomUUID(),origin,mesh,selected.station(),lane);
        var original=p;
        for(int repeat=0;repeat<100;repeat++)p=LanePoints.snap(mesh,p);
        check(p.position().distance(original.position())<1e-7,"repeated reconciliation cannot drift along curve");
        check(p.anchor().distance(original.anchor())<1e-7,"longitudinal anchor is idempotent");
        check(p.id().equals(original.id())&&p.origin()==origin&&p.lane()==lane,"canonicalization preserves point identity");
        var legacy=new LanePoints.Point(p.id(),origin,lane,p.position());
        var upgraded=LanePoints.snap(mesh,legacy);var once=upgraded;
        for(int repeat=0;repeat<100;repeat++)upgraded=LanePoints.snap(mesh,upgraded);
        check(upgraded.position().distance(once.position())<1e-7,"legacy coordinate upgraded once without repeated offset projection");
        check(upgraded.anchor()!=null,"legacy point gains a longitudinal anchor");
      }
    }
    check(legacyDrift>1e-3,"fixture reproduces the old 0.00001 stabilization threshold failure");
    var s=new Settings(Mode.STRAIGHT,Style.O6_RAIL,26,1,.4,90);
    for(double yaw:new double[]{0,37,90,180}){
      double a=Math.toRadians(yaw);V direction=new V(-Math.sin(a),0,Math.cos(a));
      var mesh=RoadGeometry.build(new Node(new V(.5,2,.5),yaw,0),new Node(new V(.5,2,.5).add(direction.mul(160)),yaw,0),s);
      for(int lane=0;lane<6;lane++)for(double off:new double[]{-1.6,-.7,0,.7,1.6}){
        var center=LanePoints.lane(mesh,60,lane);V left=RoadStructures.sample(mesh,60).left();
        var clicked=LanePoints.clicked(mesh,center.position().add(left.mul(off)));
        check(clicked.index()==lane,"off-center click retains the clicked lane");
        check(clicked.position().distance(center.position())<1e-7,"either edge of lane snaps to the same middle");
        var p=LanePoints.point(UUID.randomUUID(),LanePoints.Origin.MANUAL,mesh,clicked.station(),lane);
        var changed=p.at(LanePoints.lane(mesh,clicked.station(),(lane+3)%6),mesh);
        check(changed.anchor().distance(p.anchor())<1e-7,"switching lane retains longitudinal position");
      }
    }
    var points=new ArrayList<Sample>();double distance=0;V last=null;
    for(V v:List.of(new V(-50,0,0),new V(50,0,0),new V(50,10,50),new V(-50,10,50),new V(-50,10,0),new V(50,10,0))){if(last!=null)distance+=v.distance(last);points.add(new Sample(v,new V(0,0,1),distance,2.5));last=v;}
    var stacked=RoadRibbon.mesh(points,new Settings(Mode.CURVE,Style.O1_ONE,5,1,.35,90));
    var upper=LanePoints.point(UUID.randomUUID(),LanePoints.Origin.MANUAL,stacked,stacked.length()-50,0);
    check(Math.abs(LanePoints.snap(stacked,upper).position().y()-10)<1e-7,"self-crossing upper lane must not jump to lower layer");
    System.out.println("LanePointSnapValidation: "+checks+" checks passed; reproduced old maximum drift="+legacyDrift);
  }
}
