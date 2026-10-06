package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadProfile.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Real core geometry and terrain-history policy; not the user's screenshot world. */
public final class Finish428Validation {
 static int cases,checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void near(double a,double b,String s){check(Math.abs(a-b)<1e-6,s+": "+a+" != "+b);}
 static Settings road(Type t,int f,int r,boolean left){return RoadLanes.configure(new Settings(Mode.STRAIGHT,Style.O4_YELLOW,18,1,.4,90).options(Options.DEFAULT.traffic(left).route(Routing.DEFAULT.fit(false))),t,new RoadLanes.Counts(f,r),4);}
 static Ground ground(double h,boolean blocked){return new Ground(){public double top(double x,double z,double y){return h;}public boolean joined(V p){return false;}public boolean blocked(Part p){return blocked;}};}
 static void foundation(){
  cases++;var original=Map.of(1L,"ground",2L,"air");var fill=Map.of(1L,"air",3L,"legacyGround");
  check(RoadFoundation.source(1,"collider",true,false,original,fill,"air").equals("ground"),"collider lost original ground");
  check(RoadFoundation.source(1,"air",false,true,original,fill,"air").equals("ground"),"removed cell lost foundation");
  check(RoadFoundation.source(1,"stone",false,false,original,fill,"air").equals("stone"),"user placed block overwritten");
  check(RoadFoundation.source(2,"collider",true,false,original,Map.of(2L,"staleGround"),"air").equals("air"),"new user-cleared cell resurrected");
  check(RoadFoundation.source(3,"collider",true,false,original,fill,"air").equals("legacyGround"),"legacy map fallback lost");
  check(RoadFoundation.source(4,"air",false,true,original,fill,"air").equals("air"),"never-owned void filled");
  for(boolean left:new boolean[]{false,true}){
    cases++;var s=road(Type.ORDINARY,3,3,left);var raw=RoadGeometry.build(new Node(new V(0,2,0),0,0),new Node(new V(0,2,180),0,0),s);
    var cut=new LaneSections.Cut(UUID.randomUUID(),2,1,40,120,24,null,true,false,true);
    var m=LaneSections.apply(RoadRibbon.mesh(raw.samples(),s.options(s.options().lanePoints(LanePoints.Data.EMPTY.cuts(List.of(cut))))));
    var initial=LaneClosureLandscape.plan(m,ground(1,false));check(!initial.isEmpty(),"initial plants absent");
    check(LaneClosureLandscape.plan(m,ground(Double.NaN,false)).isEmpty(),"old original-ground-loss no longer reproduces");
    Ground rebuilt=new Ground(){public double top(double x,double z,double y){return RoadFoundation.source(1,Double.NaN,true,false,Map.of(1L,1.0),Map.of(1L,Double.NaN),Double.NaN);}public boolean joined(V p){return false;}public boolean blocked(Part p){return false;}};
    for(int i=0;i<5;i++)check(initial.equals(LaneClosureLandscape.plan(m,rebuilt)),"repeat ramp rebuild changed unrelated planter");
  }
 }
 static void skirts(){
  for(double gap:new double[]{.05,.2,.55,2.0})for(var structure:List.of(Structure.GROUND,Structure.AUTO,Structure.BRIDGE,Structure.TUNNEL)){
    cases++;var s=road(Type.ORDINARY,2,1,false).structure(structure);var m=RoadGeometry.build(new Node(new V(0,2,0),0,0),new Node(new V(0,2,20),0,0),s);
    var parts=RoadStructures.groundSkirts(m,ground(1-gap,false));boolean expected=gap<.6&&structure!=Structure.BRIDGE&&structure!=Structure.TUNNEL;
    check(parts.isEmpty()!=expected,"skirt gap policy");
    check(RoadStructures.groundSkirts(m,ground(1-gap,true)).isEmpty(),"blocked skirt placed");
    for(var p:parts){check(p.material()==Material.CONCRETE,"wrong skirt material");check(p.a().y()<=1-gap&&p.a().y()+p.height()>=1,"daylight slit remains");check(p.width()<=.1+1e-6,"skirt obstructs live lane");}
  }
 }
 static void detachment(){
  for(boolean left:new boolean[]{false,true})for(int dir:new int[]{-1,1}){
    cases++;var s=road(Type.ORDINARY,3,3,left);var raw=RoadGeometry.build(new Node(new V(0,2,0),0,0),new Node(new V(0,2,250),0,0),s);var l=RoadProfile.layout(raw,raw.first());int slot=-1;double far=-1;
    for(int i=0;i<6;i++){var lane=LanePoints.lane(raw,120,i);double d=Math.abs(lane.position().dot(raw.first().left())-l.medianCenter());if(lane.sign()==dir&&d>far){far=d;slot=i;}}
    var cuts=LaneSections.derive(raw,List.of(new LaneSections.Event(UUID.randomUUID(),LaneSections.Kind.DEPART,slot,dir,dir>0?40:210,24),new LaneSections.Event(UUID.randomUUID(),LaneSections.Kind.DEPART,slot-1,dir,120,32)));
    var m=LaneSections.apply(RoadRibbon.mesh(raw.samples(),s.options(s.options().lanePoints(LanePoints.Data.EMPTY.cuts(cuts)))));
    for(var sample:m.samples()){var layout=RoadProfile.layout(m,sample);var ref=RoadStructures.sample(raw,sample.distance());double shift=sample.center().sub(ref.center()).dot(ref.left());
      for(double d:layout.dividers()){double axis=d+shift;check(l.dividers().stream().anyMatch(v->Math.abs(v-axis)<1e-6),"removed divider dragged into diagonal merge line");}
    }
  }
 }
 static void yJunction(){
  for(var type:List.of(Type.ORDINARY,Type.HIGHWAY))for(boolean left:new boolean[]{false,true})for(int f=1;f<=4;f++)for(int r=1;r<=4;r++){
    cases++;int side=left?-1:1;var main=road(type,f,r,left);var out=road(type,f,0,left);var in=road(type,r,0,left);
    var plan=YJunctionPlanner.plan(new Node(new V(0,2,0),180,0),new Node(new V(side*80,2,-220),180,0),new Node(new V(-side*80,2,-220),0,0),main,out,in,.4);
    var stem=plan.stem();var l=RoadProfile.layout(stem,stem.last());
    for(boolean outgoing:new boolean[]{false,true}){var branch=outgoing?plan.outbound():plan.inbound();var at=outgoing?branch.first():branch.last();var bl=RoadProfile.layout(branch,at);int sign=outgoing?side:-side;double offset=at.center().sub(stem.last().center()).dot(stem.last().left());
      int expected=outgoing?f:r;check(bl.catalog().lanes()==expected,"Y throat uses wrong direction count");near(bl.laneWidth(),l.laneWidth(),"Y throat lane width");
      double lo=sign<0?-stem.last().halfWidth():l.medianEdge(1),hi=sign<0?l.medianEdge(-1):stem.last().halfWidth();
      near(offset-at.halfWidth(),lo,"Y throat lower edge");near(offset+at.halfWidth(),hi,"Y throat upper edge");
      for(double d:bl.dividers()){double axis=offset+(outgoing?1:-1)*d;check(l.dividers().stream().anyMatch(v->Math.abs(v-axis)<1e-6),"Y lane divider misaligned");}
    }
  }
 }
 static void legacyY(){
  for(boolean left:new boolean[]{false,true})for(Style style:List.of(Style.O2_YELLOW,Style.O4_GREEN,Style.O6_YELLOW,Style.H8_RAIL))for(boolean extras:new boolean[]{false,true})for(boolean unequal:new boolean[]{false,true}){
   cases++;int side=left?-1:1;var o=Options.DEFAULT.traffic(left).extras(extras,false,extras);
   var main=new Settings(Mode.CURVE,style,RoadProfile.width(style,o,3.5),1,.4,90).options(o);
   var target=RoadProfile.choose(RoadProfile.catalog(style).type(),RoadProfile.catalog(style).lanes()/2,false,Median.NONE,RoadProfile.catalog(style).shoulder());
   var outStyle=unequal?Style.O1_ONE:target;var inStyle=unequal?Style.O4_ONE:target;
   var out=new Settings(Mode.CURVE,outStyle,outStyle.defaultWidth(),1,.4,90).options(Options.DEFAULT.traffic(left));var in=new Settings(Mode.CURVE,inStyle,inStyle.defaultWidth(),1,.4,90).options(Options.DEFAULT.traffic(left));
   var plan=YJunctionPlanner.plan(new Node(new V(0,12,0),180,0),new Node(new V(side*80,12,-200),180,0),new Node(new V(-side*80,12,-200),0,0),main,out,in,.4);
   var sl=RoadProfile.layout(plan.stem(),plan.stem().last());
   for(boolean outgoing:new boolean[]{false,true}){var branch=outgoing?plan.outbound():plan.inbound();var at=outgoing?branch.first():branch.last();var bl=RoadProfile.layout(branch,at);
    var expected=sl.dividers().stream().filter(d->d*(outgoing?side:-side)>0).map(d->plan.stem().last().at(d,0)).toList();var actual=bl.dividers().stream().filter(d->d>bl.motorMin()+.001&&d<bl.motorMax()-.001).map(d->at.at(d,0)).toList();
    check(expected.size()==actual.size(),"legacy throat divider count");for(V v:expected)check(actual.stream().anyMatch(q->q.distance(v)<1e-7),"legacy throat exact seam");
    for(double d=.13;d<12;d+=.19)check(RoadSurface.painted(branch,outgoing?d:branch.length()-d)==RoadSurface.painted(plan.stem(),plan.stem().length()+d),"Y stripe phase discontinuous");
   }
  }
 }
 public static void main(String[]args){if(args.length>0&&args[0].equals("divider-only")){detachment();System.out.println("divider-only PASS "+checks);return;}foundation();skirts();detachment();yJunction();legacyY();System.out.println("Finish428Validation: "+cases+" core cases / "+checks+" checks PASS; history policy, planter preservation, slit closure, divider axes and asymmetric Y. NOT Minecraft/GPU");}
}
