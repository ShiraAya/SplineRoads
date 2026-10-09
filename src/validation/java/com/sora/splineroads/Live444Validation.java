package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

public final class Live444Validation {
  static int checks;
  static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  static Mesh straight(Style style,Structure structure){
    var options=RoadProfile.Options.DEFAULT;var settings=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,options,4),1,.35,90).structure(structure);
    return RoadGeometry.build(new Node(new V(1400,20,-700),0,0),new Node(new V(1400,20,-500),0,0),settings);
  }
  static void caps(){
    var raw=straight(Style.O3_ONE,Structure.GROUND);
    for(int slot:new int[]{0,1,2}){
      var mesh=Live435Validation.cut(raw,slot,50,150,true);var paints=LaneClosureWarnings.paint(mesh);var geometry=RoadSurface.build(mesh,List.of(),List.of());
      for(var cap:LaneDeck.caps(mesh)){
        var at=RoadQueries.horizontal(mesh,cap.a().add(cap.b()).mul(.5)).sample();var lane=LanePoints.lane(raw,at.distance(),slot);
        double center=lane.position().sub(at.center()).dot(at.left());
        for(int side:new int[]{-1,1}){
          double lateral=center+side*lane.width()/2;
          if(Math.abs(lateral)>at.halfWidth()-.6)lateral=RoadSurface.edgeOffset(mesh,at,side);
          V point=at.at(lateral,0).add(cap.b().sub(cap.a()).horizontalUnit().left().mul(.1));
          check(paints.stream().anyMatch(p->JunctionPaint.inside(p.points(),point)),"cap does not meet side stripe slot="+slot+" at="+point);
          check(geometry.markings().stream().anyMatch(p->JunctionPaint.inside(p.points(),point)),"final cap lost connected corner");
        }
      }
      var point=LanePoints.point(new UUID(444,1),LanePoints.Origin.MANUAL,raw,50,slot);
      mesh=RoadRibbon.mesh(mesh.samples(),mesh.settings().options(mesh.settings().options().lanePoints(mesh.settings().options().lanePoints().points(List.of(point)))));
      var ref=LanePoints.Ref.lane(new UUID(444,2),point.id());var link=new LanePoints.Link(ref,LanePoints.Ref.lane(new UUID(444,3),new UUID(444,4)),LanePoints.Options.DEFAULT,null);
      var at=LanePoints.lane(raw,50,slot);var samples=List.of(new Sample(at.position(),at.direction().left(),0,at.width()/2),new Sample(at.position().add(at.direction().mul(12)),at.direction().left(),12,at.width()/2));
      var ramp=RoadRibbon.mesh(samples,new Settings(Mode.STRAIGHT,Style.C1_RAMP,at.width(),1,.35,90).options(RoadProfile.Options.DEFAULT.lanePoints(LanePoints.Data.EMPTY.link(link))));
      var mid=at.position().add(at.direction().mul(-.1));
      check(LaneClosureWarnings.paint(mesh,List.of(ramp)).stream().noneMatch(p->JunctionPaint.inside(p.points(),mid)),"closure transverse stripe crosses live connector mouth");
    }
  }
  static Ground ground(Mesh other,boolean owner){
    var join=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(other,owner)));
    return new Ground(){public double top(double x,double z,double y){return 0;}public boolean joined(V p){return false;}
      public boolean blocked(Part p){return RoadClearance.structureInvades(p,other,4.25);}
      public boolean railBlocked(Part p,V a,V b){return !RoadRailJoin.sharedRail(other,a,b)&&blocked(p);}
      public List<RoadRailJoin.Span> railSpans(V a,V b,V outside){return join.exposed(a,b,outside);}
      public List<RoadRailJoin.Span> railSpans(V a,V b,V outside,double inset){return join.exposed(a,b,outside,inset);}
      public boolean railPost(V p,boolean highway,boolean raised){return join.ownsPost(p,highway,raised);}
    };
  }
  static void common(){
    var whole=straight(Style.C1_RAMP,Structure.BRIDGE);
    var half=RoadRibbon.mesh(whole.samples().stream().filter(s->s.distance()>=40&&s.distance()<=90).map(s->new Sample(s.center(),s.left(),s.distance()-40,s.halfWidth())).toList(),whole.settings());
    var a=RoadStructures.plan(whole,ground(half,false));var b=RoadStructures.plan(half,ground(whole,true));
    for(double d=45;d<85;d+=2)for(int side:new int[]{-1,1}){
      var at=RoadStructures.sample(whole,d);var point=at.at(side*(at.halfWidth()-RoadRailJoin.INSET),0);
      check(a.stream().anyMatch(p->p.material()==Material.CONCRETE&&p.height()==.45&&JunctionPaint.inside(p.base(),point)),"owned shared rail erased by neighbour clearance at="+point);
      check(b.stream().noneMatch(p->p.material()==Material.CONCRETE&&p.height()==.45&&JunctionPaint.inside(p.base(),point)),"duplicate shared rail");
      var paint=at.at(RoadSurface.edgeOffset(whole,at,side),0);
      check(RoadSurface.build(whole,List.of(),List.of(half)).markings().stream().anyMatch(f->JunctionPaint.inside(f.points(),paint)),"shared outer white line erased from both roads");
    }
    V inner=RoadStructures.sample(whole,60).center();check(!RoadRailJoin.sharedRail(whole,inner,inner.add(new V(0,0,1))),"live lane interior treated as shared perimeter");
  }
  static void vertical(){
    var points=new ArrayList<Sample>();for(int i=0;i<=400;i++)points.add(new Sample(new V(0,20,i*.5),new V(-1,0,0),i*.5,2));
    var base=RoadRibbon.mesh(points,new Settings(Mode.CURVE,Style.C1_RAMP,4,1,.35,90));
    var solved=LaneRampCorridor.solve(base,0,200,List.of(new LaneRampHeights.Constraint(80,120,8)),true,.2);
    double previous=0,worst=0;
    for(int i=1;i<solved.samples().size();i++){
      var a=solved.samples().get(i-1);var b=solved.samples().get(i);double grade=(b.center().y()-a.center().y())/.5;
      if(i>1)worst=Math.max(worst,Math.abs(grade-previous));previous=grade;
      check(Math.abs(grade)<=.200001,"smoothing exceeded grade");
      if(b.distance()>=80&&b.distance()<=120)check(b.center().y()>=28-1e-6,"smoothing erased obstacle clearance");
    }
    check(worst<.02,"vertical grade kink remains "+worst);
    System.out.println("LIVE444 max adjacent grade change="+worst);
  }
  public static void main(String[] args){caps();common();vertical();System.out.println("Live444Validation "+checks+" checks PASS");}
}
