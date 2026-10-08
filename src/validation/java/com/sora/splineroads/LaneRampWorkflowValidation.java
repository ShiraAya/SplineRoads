package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
public final class LaneRampWorkflowValidation {
 static int checks;static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 public static void main(String[] args){
  for(var style:List.of(Style.O2_ONE,Style.O4_YELLOW,Style.H2_ONE))for(boolean traffic:List.of(false,true))for(boolean curved:List.of(false,true)){
   var options=RoadProfile.Options.DEFAULT.traffic(traffic);var settings=new Settings(curved?Mode.CURVE:Mode.STRAIGHT,style,RoadProfile.width(style,options,4),1,.35,90).options(options);
   var host=RoadGeometry.build(new Node(new V(0,100,0),0,0),new Node(new V(curved?40:0,100,300),curved?20:0,0),settings);
   for(int lane=0;lane<RoadProfile.catalog(style).lanes();lane++)for(boolean source:List.of(false,true)){
    double station=host.length()/2;var samples=LaneRampApproach.build(host,lane,station,source,5,32);var selected=LanePoints.lane(host,station,lane);
    check((source?samples.get(0):samples.get(samples.size()-1)).center().distance(selected.position())<1e-6,"taper attaches to selected axis");
    int full=0;
    for(int i=0;i<samples.size();i++){var s=samples.get(i);var q=RoadQueries.horizontal(host,s.center());if(source?i>=64:i<=64){full++;check(Math.abs(q.horizontalDistance()-q.sample().halfWidth()-2.48)<.06,"whole extra lane lies outside host, including bends");check(Math.abs(q.sample().left().dot(s.left()))>.99,"full auxiliary stretch follows curved host");}}
    check(full>=63,"at least 32 block full-width parallel lane, not only an offset curve");
    boolean denied=false;try{LaneRampApproach.build(host,lane,selected.sign()>0?(source?host.length():0):(source?0:host.length()),source,5,32);}catch(IllegalArgumentException e){denied=true;}check(denied,"extra lane cannot extend beyond nonexistent host");
   }
  }
  for(boolean order:List.of(false,true)){
   UUID hostId=new UUID(0,order?1:9),rampId=new UUID(0,order?9:1);var s=new Settings(Mode.STRAIGHT,Style.O2_ONE,8,1,.35,90);
   var host=RoadGeometry.build(new Node(new V(0,100,0),0,0),new Node(new V(0,100,140),0,0),s);
   var link=new LanePoints.Link(LanePoints.Ref.lane(hostId,UUID.randomUUID()),LanePoints.Ref.lane(UUID.randomUUID(),UUID.randomUUID()),LanePoints.Options.DEFAULT,null);
   var branch=RoadGeometry.build(new Node(new V(-2,100,0),0,0),new Node(new V(-2,100,140),0,0),new Settings(Mode.STRAIGHT,Style.O1_ONE,5,1,.35,90).options(RoadProfile.Options.DEFAULT.lanePoints(LanePoints.Data.EMPTY.link(link))));
   check(RoadSurface.higherPriority(hostId,host,rampId,branch)&&!RoadSurface.higherPriority(rampId,branch,hostId,host),"host paint ownership ignores UUID ordering");
   var before=RoadSurface.build(host,List.of(),List.of());var after=RoadSurface.build(host,List.of(),List.of(branch));
   for(var face:before.markings()){
    V center=face.points().stream().reduce(new V(0,0,0),V::add).mul(1d/face.points().size());
    if(Math.abs(center.x())<1.5)check(after.markings().contains(face),"original mainroad divider/arrow survives coplanar connector");
   }
   var sub=new LanePoints.Link(LanePoints.Ref.lane(rampId,UUID.randomUUID()),link.to(),LanePoints.Options.DEFAULT,null);
   var child=RoadRibbon.mesh(branch.samples(),branch.settings().options(branch.settings().options().lanePoints(LanePoints.Data.EMPTY.link(sub).priorityDepth(2))));
   check(RoadSurface.higherPriority(rampId,branch,new UUID(0,0),child),"a parent ramp owns its paint at a downstream branch");
  }

  var from=LanePoints.Ref.lane(UUID.randomUUID(),UUID.randomUUID());var to=LanePoints.Ref.lane(UUID.randomUUID(),UUID.randomUUID());
  var link=new LanePoints.Link(from,to,LanePoints.Options.DEFAULT,null);
  for(boolean cycle:List.of(false,true)){
   var options=RoadProfile.Options.DEFAULT.cycleFinish(cycle?RoadProfile.Options.CycleFinish.ASPHALT:RoadProfile.Options.CycleFinish.NONE).lanePoints(LanePoints.Data.EMPTY.link(link));
   var s=new Settings(Mode.STRAIGHT,Style.O1_ONE,RoadProfile.width(Style.O1_ONE,options,4),1,.35,90).options(options);
   var path=RoadGeometry.build(new Node(new V(0,100,0),0,0),new Node(new V(0,100,240),0,0),s);var fitted=LaneRampAlignment.fit(path,3,5,32);
   check(LaneRampAlignment.axis(fitted,true).distance(path.first().center())<1e-6&&LaneRampAlignment.axis(fitted,false).distance(path.last().center())<1e-6,"motor axes stay exactly on selected lanes, including asymmetric roadside width");
   check(Math.abs(LanePoints.lane(fitted,0,0).width()-3)<1e-6&&Math.abs(LanePoints.lane(fitted,fitted.length(),0).width()-5)<1e-6,"both mouths match different real host lane widths");
   var above=LaneRampHeights.adjust(path,40,200,6);var below=LaneRampHeights.adjust(path,40,200,-6);
   check(RoadStructures.sample(above,120).center().y()>105.9&&RoadStructures.sample(below,120).center().y()<94.1,"overpass and underpass change real samples");
   check(RoadStructures.sample(above,30).center().y()==100&&RoadStructures.sample(above,210).center().y()==100,"host contact portions are not lifted");
   boolean rejected=false;try{LaneRampHeights.adjust(path,100,120,6);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"insufficient vertical transition cannot bypass grade limit");
  }
  System.out.println("LaneRampWorkflowValidation: "+checks+" checks passed");
 }
}
