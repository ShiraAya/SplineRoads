package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import com.sora.splineroads.core.JunctionSpec.*;
import static com.sora.splineroads.core.JunctionSpec.*;
import java.util.*;
public final class Revision251Validation {
 static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static void signals(){
  for(boolean left:new boolean[]{false,true})for(int green:new int[]{19,20}) {
   var s=Junction22Validation.fixture(Style.O6_RAIL,left,0,90,180,270);var arms=new ArrayList<Arm>();
   for(int i=0;i<s.arms().size();i++){
    Arm a=s.arms().get(i);var lanes=new ArrayList<>(a.lanes());
    if(i==0){lanes.set(0,lanes.get(0).mask(LEFT|STRAIGHT).signals(true,15));lanes.set(1,lanes.get(1).mask(LEFT|STRAIGHT).signals(true,7));}
    // A and B deliberately share a phase; only A has a protected arrow.
    arms.add(new Arm(a.endpoint(),a.inward(),a.external(),a.incoming(),a.outgoing(),a.width(),a.median(),a.medianKind(),a.cycleWidth(),a.curbWidth(),a.crosswalk(),a.crossingWidth(),a.crossingSetback(),a.stopGap(),i<2?0:1,lanes,a.attached()));
   }
   s=new JunctionSpec(s.center(),s.kind(),left,s.cornerRadius(),s.islandRadius(),s.ringLanes(),s.ringLaneWidth(),s.thickness(),Control.SIGNALS,green,3,1,0,s.guides(),s.greenIsland(),s.outerRail(),arms);
   var p=JunctionPlanner.plan(s);var c=p.signals();check(c.periodSeconds()==2*(green+4),"left lamps add no phase slots");
   int aGreen=0,leftGreen=0,bGreen=0;
   for(int tick=0;tick<c.periodSeconds()*20;tick++){
    int straight=c.vehicle(0,tick),turn=c.vehicle(0,0,true,tick),other=c.vehicle(1,tick);
    if(straight==1)aGreen++;if(turn==1)leftGreen++;if(other==1)bGreen++;
    check(straight==0||turn==0,"straight and left never overlap on A");
    check(turn==c.vehicle(0,1,true,tick),"obsolete per-lane left phase values are ignored");
    if(tick<green*10)check(straight==1&&turn==0&&other==1,"first exact half: A and B straight");
    else if(tick<green*20)check(straight==0&&turn==1&&other==1,"second exact half: A left, B still straight");
    else if(tick<(green+3)*20)check(straight==0&&turn==2&&other==2,"existing end yellow belongs to active heads");
    check(c.vehicle(0,tick)==c.vehicle(0,tick-c.periodSeconds()*20L),"negative clock wraps exactly");
   }
   check(aGreen==green*10&&leftGreen==green*10&&bGreen==green*20,"green split preserves exact ticks including odd seconds");
   var parts=p.pieces().get(0).structures();check(parts.stream().filter(q->q.material()==RoadStructures.Material.SIGNAL_LEFT).count()==1,"one grouped left head even with obsolete distinct phases");
   var ref=new JunctionPlanner.Ref(s,0);var heads=parts.stream().filter(RoadSignals::signal).toList();
   var frames=new RoadSignalFrames(heads,ref::headState,c.resolutionTicks());
   var before=frames.at(green*10L-1);var after=frames.at(green*10L);
   check(before!=after,"visible lamp cache changes exactly at the half-green tick");
   var legacy=new JunctionPlanner.Ref(s,0,25);check(legacy.get().structures().stream().filter(q->q.material()==RoadStructures.Material.SIGNAL_LEFT).count()==2,"saved 25 physical head positions remain reproducible");
  }
 }
 static void heights(){
  var s=Revision24Validation.main(Style.H4_RAIL);
  for(int n:new int[]{5,6}){
   Node[] nodes=Revision24Validation.nodes(n,900,0,0,90,0);double[] h={90,130,110};
   for(int i=0;i<n;i++){V p=nodes[i].position();nodes[i]=new Node(new V(p.x(),h[i/2],p.z()),0,0);}
   var o=new Options(n==5?Preset.DIRECTIONAL_FIVE:Preset.DIRECTIONAL_SIX,false,1,96,20,5,2,0,true,true,64);
   var p=MultiInterchange.plan(nodes,new Settings[]{s,s,s},o,90);
   check(p.anchors().get(0).position().y()==90,"selected lowest level retained");
   check(p.anchors().get(2).position().y()==103&&p.anchors().get(4).position().y()==96.5,"lowering compresses gaps while preserving AB < EF < CD");
   for(int i=0;i<n;i++)check(p.anchors().get(i).position().y()>=h[i/2]-64,"bounded lowering");
   System.out.println("HEIGHT "+n+" -> "+p.anchors().stream().map(a->a.position().y()).toList());
  }
 }
 static void eSections(){
  var main=Revision24Validation.main(Style.H4_RAIL);
  for(Style style:new Style[]{Style.O2_YELLOW,Style.O4_RAIL,Style.H4_RAIL,Style.H6_RAIL})for(int lanes:new int[]{1,2}){
   var e=Revision24Validation.main(style);var o=new Options(Preset.DIRECTIONAL_FIVE,false,lanes,96,20,5,2,0,true,true,0);
   var p=MultiInterchange.plan(Revision24Validation.nodes(5,640,0,0,64,0),new Settings[]{main,main,e},o,64);
   var m=p.legs().get(2).mesh();var layout=RoadTransitions.layout(m,m.last());var old=RoadProfile.layout(e,e.width());
   check(layout.catalog().lanes()==4,"E keeps two lanes per direction before splitting");check(layout.shoulderWidth()==0,"E emergency shoulder is removed");
   check(Math.abs(layout.laneWidth()-old.laneWidth())<1e-8,"E keeps original lane width");
   double base=m.last().center().sub(p.center()).horizontalLength();var radial=m.first().center().sub(p.center()).horizontalUnit();Set<Long> ports=new HashSet<>();
   for(var leg:p.legs())if(leg.mesh().settings().style().ramp())for(Sample q:leg.mesh().samples())if(Math.abs(q.center().sub(p.center()).dot(radial)-base)<1e-5)ports.add(Math.round(q.center().sub(p.center()).dot(radial.left())*1e6));
   check(ports.size()==2,"E has one common collector per direction before binary splitting");
   System.out.println("E "+style+" ramp lanes="+lanes+" width "+e.width()+" -> "+(m.last().halfWidth()*2)+", radius "+p.anchors().get(0).position().sub(p.center()).horizontalLength());
  }
 }
 public static void main(String[] args){signals();heights();eSections();System.out.println("Revision251 PASS "+checks+" checks");}
}
