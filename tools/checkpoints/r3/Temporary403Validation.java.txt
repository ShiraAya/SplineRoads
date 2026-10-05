package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
/** Production clearance/cut geometry. No Minecraft, world writes or GPU simulation. */
public final class Temporary403Validation {
  static int checks,cases;
  static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
  static Settings settings(Style style){var o=RoadProfile.Options.DEFAULT;return new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,o,4),1,.35,90).options(o);}
  static Mesh host(boolean left){return RoadGeometry.build(new Node(new V(0,100,0),0,0),new Node(new V(0,100,1400),0,0),settings(Style.O4_RAIL).options(RoadProfile.Options.DEFAULT.traffic(left)));}
  static Mesh ramp(Mesh host,int slot,double begin,int sign,double grade,boolean loop,double slab){
    var p=new ArrayList<Sample>();for(double d=0;d<=800;d++){
      var lane=LanePoints.lane(host,begin+sign*d,slot);double offset=d<350?0:Math.min(80,(d-350)*.4),height=Math.min(18,Math.abs(grade)*d)*Math.signum(grade);
      if(loop&&d>=450&&d<=750){offset=Math.abs(d-550)*.4;double low=Math.min(1,Math.min((d-450)/60,(750-d)/60));height=Math.signum(grade)*(18-16*low);}
      p.add(new Sample(lane.position().add(new V(offset,height,0)),new V(sign,0,0),d,2.5));
    }
    return RoadRibbon.mesh(p,new Settings(Mode.STRAIGHT,Style.O1_ONE,5,slab,.35,90));
  }
  static double restored(Mesh host,int slot,double begin,Mesh ramp,int sign,List<RoadStructures.Part> parts){
    double end=LaneReopening.restoreStation(host,slot,begin,ramp,parts,32);var id=new UUID(403,++cases);
    var cuts=LaneSections.derive(host,List.of(new LaneSections.Event(id,LaneSections.Kind.TEMPORARY,slot,sign,begin,32,end)));
    var raw=RoadRibbon.mesh(host.samples(),host.settings().options(host.settings().options().lanePoints(LanePoints.Data.EMPTY.cuts(cuts))));var effective=LaneSections.apply(raw);
    LaneReopening.validateRestored(effective,slot,ramp,id);checks++;
    check(!LaneSections.active(effective,begin+sign*40,slot),"selected host lane must physically close");
    check(LaneSections.active(effective,end+sign*4,slot),"selected host lane must reopen");
    return sign*(end-begin);
  }
  public static void main(String[]args){
    for(boolean left:new boolean[]{false,true})for(int slot:new int[]{1,3})for(int vertical:new int[]{-1,1}){
      var h=host(left);int sign=LanePoints.lane(h,700,slot).sign();double begin=sign>0?200:1200;
      var shallow=ramp(h,slot,begin,sign,vertical*.03,false,.35);var steep=ramp(h,slot,begin,sign,vertical*.09,false,.35);
      double a=restored(h,slot,begin,shallow,sign,List.of()),b=restored(h,slot,begin,steep,sign,List.of());check(a>b+40,"restoration depends on actual vertical clearance");
      double c=restored(h,slot,begin,ramp(h,slot,begin,sign,vertical*.09,true,.35),sign,List.of());check(c>b+300,"last obstructing loop governs restoration");
      double thick=restored(h,slot,begin,ramp(h,slot,begin,sign,vertical*.03,false,2),sign,List.of());check(vertical>0?thick>a+35:Math.abs(thick-a)<1e-6,"upper slab thickness only governs gap");
      var at=LanePoints.lane(h,begin+sign*500,slot).position();var pier=new RoadStructures.Part(at.sub(new V(0,20,0)),at.sub(new V(0,20,0)),1,30,true);
      double supported=restored(h,slot,begin,steep,sign,List.of(pier));check(supported>b+300,"pier crossing live slot delays restoration");
      var clearPier=new RoadStructures.Part(at.add(new V(0,6,0)),at.add(new V(0,6,0)),1,30,true);double above=restored(h,slot,begin,steep,sign,List.of(clearPier));check(Math.abs(above-b)<1e-6,"high structure does not block live slot");
    }
    var h=host(false);var lane=LanePoints.lane(h,200,3);var p=new ArrayList<Sample>();for(double d=200;d<=1400;d++)p.add(new Sample(LanePoints.lane(h,d,3).position(),new V(-1,0,0),d-200,2.5));
    boolean refused=false;try{LaneReopening.restoreStation(h,3,200,RoadRibbon.mesh(p,settings(Style.O1_ONE)),List.of(),32);}catch(IllegalArgumentException e){refused=true;}check(refused,"cannot reopen beyond road end");
    System.out.println("Temporary403Validation: "+cases+" geometric cases, "+checks+" checks PASS (actual core, NOT game/GPU)");
  }
}
