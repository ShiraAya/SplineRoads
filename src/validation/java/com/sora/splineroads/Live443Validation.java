package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
public final class Live443Validation {
  static int checks;
  static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
  static void closures(){
    var settings=Live435Validation.road(Style.O3_ONE,Structure.GROUND).settings();
    var raw=RoadGeometry.build(new Node(new V(0,20,0),0,0),new Node(new V(0,20,200),0,0),settings);
    for(int slot:new int[]{0,2}){
      var host=Live435Validation.cut(raw,slot,50,150,true);
      check(LaneClosureLandscape.plan(host,Live435Validation.ground(19)).stream().anyMatch(p->p.material()==Material.GREEN),"spacious exterior closed slot lost planting (0446)");
      check(!LaneClosureWarnings.paint(host).isEmpty(),"ground cut has no boundary paint");
      for(var cap:LaneDeck.caps(host)){
        V mid=cap.a().add(cap.b()).mul(.5);
        check(LaneClosureWarnings.paint(host).stream().flatMap(p->p.points().stream()).anyMatch(v->Math.abs(v.z()-mid.z())<.3),"missing transverse closure line");
        V point=mid.add(cap.b().sub(cap.a()).horizontalUnit().left().mul(.10));
        check(RoadSurface.build(host,List.of(),List.of()).markings().stream().anyMatch(f->JunctionPaint.inside(f.points(),point)),"transverse boundary paint clipped away from final road surface");
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
    var stacked=RoadRibbon.mesh(parent.stream().map(p->new Sample(p.center().add(new V(0,p.distance()*.08,0)),p.left(),p.distance(),p.halfWidth())).toList(),settings);
    boolean denied=false;try{LaneRampThroat.fit(stacked,List.of(host),true);}catch(IllegalArgumentException expected){denied=true;}
    check(denied,"fully coincident ordinary fork accepted stacked elevation");
  }
  static void collision(){
    var road=Live435Validation.road(Style.C1_RAMP,Structure.BRIDGE);
    var p=new Part(new V(0,0,100),new V(0,0,100),1.5,40,true,Material.CONCRETE);
    check(RoadClearance.structureInvades(p,road,4.25),"pier crossing lower live ramp accepted");
    check(!RoadClearance.structureInvades(new Part(new V(12,0,100),new V(12,0,100),1.5,40,true,Material.CONCRETE),road,4.25),"unrelated support rejected");
  }
  static void fascia(){
    var lower=Live435Validation.road(Style.C1_RAMP,Structure.BRIDGE);
    var points=new ArrayList<Sample>();for(int i=0;i<=160;i++)points.add(new Sample(new V(-40+i*.5,25.3,100),new V(0,0,-1),i*.5,2));
    var upper=RoadRibbon.mesh(points,lower.settings());
    check(RoadClearance.contacts(upper,lower).stream().noneMatch(RoadClearance.Contact::blocked),"safe fascia crossing rejected");
    var tight=RoadRibbon.mesh(points.stream().map(p->new Sample(p.center().add(new V(0,-.1,0)),p.left(),p.distance(),p.halfWidth())).toList(),lower.settings());
    check(RoadClearance.contacts(tight,lower).stream().anyMatch(RoadClearance.Contact::blocked),"planner still accepts 4.2 while fascia requires 4.25");
    Ground g=new Ground(){public double top(double x,double z,double y){return 0;}public boolean joined(V p){return false;}public boolean blocked(Part p){return RoadClearance.structureInvades(p,lower,4.25);}};
    var slabs=RoadStructures.edgeSlabs(upper,g);
    for(int side:new int[]{-1,1})check(slabs.stream().anyMatch(p->p.a().x()<=0&&p.b().x()>=0&&side*(p.a().z()-100)>2),"fascia missing directly over lower road");
  }
  static void seams(){
    var raw=Live435Validation.road(Style.C1_RAMP,Structure.BRIDGE);
    check(RoadRailJoin.mouths(List.of(raw)).exposedMouth(new V(-1.5,20,0),new V(1.5,20,0)).isEmpty(),"transverse mouth was mistaken for a parallel seam");
    var ref=LanePoints.Ref.lane(new UUID(443,2),new UUID(443,3));
    var link=new LanePoints.Link(ref,LanePoints.Ref.lane(new UUID(443,4),new UUID(443,5)),LanePoints.Options.DEFAULT,null);
    var settings=raw.settings().options(raw.settings().options().hideArrows(true).lanePoints(LanePoints.Data.EMPTY.link(link)));var host=RoadRibbon.mesh(raw.samples(),settings);
    for(int mirror:new int[]{-1,1}){
      var points=new ArrayList<Sample>();
      for(int i=0;i<=200;i++){double d=i*.5;V tangent=new V(mirror*.01*d,0,1).horizontalUnit();points.add(new Sample(new V(mirror*.005*d*d,20,50+d),tangent.left(),d,2));}
      var ramp=RoadRibbon.mesh(points,settings);
      var parts=new ArrayList<List<Part>>();
      for(var mesh:List.of(host,ramp)){
        var other=mesh==host?ramp:host;var joins=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(other,true)));
        Ground g=new Ground(){public double top(double x,double z,double y){return 0;}public boolean joined(V p){return false;}public boolean blocked(Part p){return false;}
          public List<RoadRailJoin.Span> railSpans(V a,V b,V outside,double inset){return joins.exposed(a,b,outside,inset);}
          public List<RoadRailJoin.Span> railSpans(V a,V b,V outside){return joins.exposed(a,b,outside);}
          public V railJoint(V p,V d,boolean highway,boolean raised){return joins.joint(p,d,highway,raised);}
        };
        parts.add(RoadStructures.plan(mesh,g).stream().filter(p->!p.pier()&&p.material()==Material.CONCRETE&&Math.abs(p.a().y()-20)<1e-6&&Math.abs(p.height()-.45)<1e-6).toList());
        for(var paint:RoadSurface.build(mesh,List.of(),List.of(other)).markings()){
          V center=paint.points().stream().reduce(new V(0,0,0),V::add).mul(1d/paint.points().size());
          // The shared exterior stripe belongs on the union's inset perimeter;
          // raw pavement includes that shoulder and cannot be used as a paint veto.
          check(!RoadQueries.contains(other,center,-.301,.025),"edge paint remains inside joining traffic area host="+(mesh==host)+" center="+center+" points="+paint.points());
        }
      }
      var main=parts.get(0).stream().filter(p->p.a().z()>60&&p.b().z()<140&&mirror*p.a().x()>0).toList();
      check(!main.isEmpty(),"straight host edge vanished");
      for(var p:main)check(Math.abs(mirror*p.a().x()-(host.first().halfWidth()-RoadRailJoin.INSET))<1e-7&&Math.abs(mirror*p.b().x()-(host.first().halfWidth()-RoadRailJoin.INSET))<1e-7,"straight host rail indents at fork: "+p+" expected="+(host.first().halfWidth()-RoadRailJoin.INSET));
      var branch=parts.get(1).stream().filter(p->p.a().z()>60&&p.b().z()<100&&mirror*p.a().x()<5).toList();
      double nearest=100;
      for(var a:main)for(var b:branch)for(var p:List.of(a.a(),a.b()))for(var q:List.of(b.a(),b.b()))nearest=Math.min(nearest,p.distance(q));
      check(nearest<.01,"fork rail tips do not meet: "+nearest);
    }
  }
  public static void main(String[]args){closures();arrows();sharedHeight();collision();fascia();seams();System.out.println("Live443Validation "+checks+" checks PASS");}
}
