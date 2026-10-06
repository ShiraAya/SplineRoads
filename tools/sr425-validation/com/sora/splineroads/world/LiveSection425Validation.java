package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import java.util.*;

/** Real RoadIndex, RoadRecord and core, with BlockPos/NBT adapters. No world/GPU claim. */
public final class LiveSection425Validation {
  static int checks,cases;
  static void check(boolean ok,String msg){checks++;if(!ok)throw new AssertionError(msg);}
  static void near(V a,V b,String msg){check(a.distance(b)<1e-7,msg+": "+a+" != "+b);}
  static RoadRecord road(Style style,boolean left,boolean raised){
    var options=RoadProfile.Options.DEFAULT.traffic(left);
    var settings=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,options,4),1,.35,90).options(options).structure(raised?Structure.AUTO:Structure.GROUND);
    return new RoadRecord(new UUID(425,++cases),new UUID(425,0),new BlockPos(0,100,0),new BlockPos(0,100,400),
        new Node(new V(.5,100,.5),0,0),new Node(new V(.5,100,400.5),0,0),settings,true,4);
  }
  static void indexRoundTrip(Style style,boolean left,boolean raised){
    var r=road(style,left,raised);int count=RoadProfile.catalog(style).lanes(),slot=count-1;
    var point=LanePoints.point(new UUID(425,1000+cases),LanePoints.Origin.MANUAL,r.rawMesh(),200,slot).merge(32);
    var cuts=LaneSections.derive(r.rawMesh(),List.of(LaneMerge.event(r.rawMesh(),point)));
    r=r.withLanePoints(new LanePoints.Data(List.of(point),null,List.of(),0,cuts));
    var built=new RoadIndex.Built(r);var original=built.mesh;
    double downstream=LanePoints.lane(original,200,slot).sign()>0?350:50;
    var marker=LanePoints.point(new UUID(425,2000+cases),LanePoints.Origin.MANUAL,original,downstream,slot-1);
    var metadata=built.lanePoints(LaneTopology.metadata(r).points(List.of(point,marker)));
    check(metadata.mesh.reference()!=null,"metadata-only edit dropped authored slot reference");
    near(LanePoints.lane(metadata.mesh,marker).position(),marker.position(),"click/snap shifted marker to divider");
    check(metadata.mesh.samples().equals(metadata.record.mesh().samples()),"Built/record narrowed surfaces disagree");
    var spans=raised?List.of(new RoadStreetscape.Span(0,400)):List.<RoadStreetscape.Span>of();
    var planned=metadata.planned(metadata.record.derivedStreetscape(metadata.record.settings().options().streetscape().raisedSpans(spans)));
    check(planned.mesh.reference()!=null,"planned update lost authored frame");
    check(planned.mesh.samples().equals(planned.record.mesh().samples()),"raised planning reused stale samples");
    near(LanePoints.lane(planned.mesh,marker).position(),LanePoints.lane(planned.record.mesh(),marker).position(),"plan/resnap cannot stabilize");
    var read=RoadRecord.load(planned.record.save());var loaded=RoadIndex.Built.loading(read);
    near(LanePoints.lane(loaded.mesh,marker).position(),LanePoints.lane(planned.mesh,marker).position(),"reload changed lane center");
    var live=LaneSections.live(planned.mesh,downstream);
    check(live.lanes().size()==count-1,"current count includes merged-away lane");
    check(LaneSections.edge(planned.mesh,downstream,slot-1),"former inner lane did not become edge");
    for(var lane:live.lanes())check(lane.index()!=slot,"vacancy became an active lane");
    boolean blocked=false;try{metadata.lanePoints(LaneTopology.metadata(metadata.record).cuts(List.of()));}catch(IllegalArgumentException expected){blocked=true;}
    check(blocked,"metadata fast path accepted geometry change");
  }
  static void sequential(boolean left,boolean twoWay){
    var r=road(twoWay?Style.O6_YELLOW:Style.O3_ONE,left,false);var raw=r.rawMesh();int count=RoadProfile.catalog(raw.settings().style()).lanes(),outer=count-1;
    int sign=LanePoints.lane(raw,200,outer).sign();double a=sign>0?60:340,b=sign>0?180:220;
    var first=new LaneSections.Event(new UUID(425,3000+cases),LaneSections.Kind.DEPART,outer,sign,a,32);
    var second=new LaneSections.Event(new UUID(425,4000+cases),LaneSections.Kind.DEPART,outer-1,sign,b,32);
    var cuts=LaneSections.derive(raw,List.of(second,first));var effective=r.withLanePoints(LanePoints.Data.EMPTY.cuts(cuts)).mesh();
    check(LaneSections.live(effective,sign>0?320:80).count(sign)==1,"sequential live-edge drops did not leave one lane");
    check(LaneSections.live(effective,200).lanes().stream().allMatch(l->l.width()>0),"invalid live lane");
    check(LaneSections.live(effective,sign>0?30:370).count(sign)==3,"upstream count changed");
    check(LaneSections.live(effective,sign>0?130:270).count(sign)==2,"intermediate count not 2");
    var p=LanePoints.point(new UUID(425,5000+cases),LanePoints.Origin.MANUAL,raw,b,outer-1).merge(32);
    var once=r.withLanePoints(LanePoints.Data.EMPTY.cuts(LaneSections.derive(raw,List.of(first)))).mesh();
    check(LaneMerge.event(once,p).lane()==outer-1,"new outer lane cannot merge");
    var restored=r.withLanePoints(LanePoints.Data.EMPTY).mesh();
    check(!LaneSections.edge(restored,b,outer-1),"deletion did not restore original outer identity");
  }
  public static void main(String[] args){
    for(var style:List.of(Style.O3_ONE,Style.O4_YELLOW,Style.O6_GREEN,Style.H6_RAIL))for(boolean left:new boolean[]{false,true})for(boolean raised:new boolean[]{false,true})indexRoundTrip(style,left,raised);
    for(boolean left:new boolean[]{false,true})for(boolean twoWay:new boolean[]{false,true})sequential(left,twoWay);
    System.out.println("LiveSection425Validation: "+cases+" cases / "+checks+" checks PASS; actual RoadIndex + core, NBT/BlockPos adapters; NOT Minecraft/GPU/world writes.");
  }
}
