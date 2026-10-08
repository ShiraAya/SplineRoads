package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** The second screenshot batch: continuous protection, sealed closures and round returns. */
public final class Live436Validation {
  static int checks;
  static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
  static void sealing(){
    var raw=Live435Validation.road(Style.O3_ONE,Structure.GROUND);
    var mesh=Live435Validation.cut(raw,1,50,80,true);
    var parts=LaneClosureLandscape.plan(mesh,Live435Validation.ground(19.8));
    for(double z:new double[]{50.01,50.5,51.5,78.5,79.5,79.99})for(double x:new double[]{-.499,0,.499}){
      V p=LanePoints.lane(raw,z,1).position().add(new V(x*LanePoints.lane(raw,z,1).width(),0,0));
      check(parts.stream().anyMatch(q->q.material()==Material.CONCRETE&&q.a().y()+q.height()<20.03&&JunctionPaint.inside(q.base(),p)),"grass/daylight corner at "+p);
    }
    for(var p:parts)if(p.material()==Material.SOIL)for(var v:p.base())check(v.z()>=50.2-1e-6&&v.z()<=79.8+1e-6,"soil protrudes beyond sealed end cap");
    var surface=RoadSurface.build(mesh,List.of(),List.of());
    for(double d:new double[]{47.5,48.5,49.5})for(int side:new int[]{-1,1}){
      var l=LanePoints.lane(raw,d,1);V p=l.position().add(new V(side*l.width()/2,0,0));
      check(LaneClosureWarnings.covers(mesh,d,1),"last three metres excluded from warning");
      check(surface.markings().stream().anyMatch(f->f.color()==0xEDEEE2&&JunctionPaint.inside(f.points(),p)),"dashed gap immediately before greenery");
    }
  }
  static void terminal(){
    var raw=Live435Validation.road(Style.O3_ONE,Structure.BRIDGE);
    for(boolean first:new boolean[]{true,false}){
      var m=Live435Validation.cut(raw,1,first?0:100,first?100:200,first);
      var at=first?m.first():m.last();var l=LanePoints.lane(raw,at.distance(),1);double x=l.position().sub(at.center()).dot(at.left());
      check(!LaneDeck.present(m,at,x,0),"terminal closed slot becomes present again");
      check(LaneDeck.spans(m,at).stream().noneMatch(s->x>s.low()+1e-6&&x<s.high()-1e-6),"transverse ghost slab at terminal");
      var surface=RoadSurface.build(m,List.of(),List.of());
      for(var face:surface.pavement())if(face.texture()==RoadSurface.Texture.CONCRETE&&face.points().stream().allMatch(p->Math.abs(p.z()-at.center().z())<1e-7)){
        double lo=face.points().stream().mapToDouble(V::x).min().orElse(0),hi=face.points().stream().mapToDouble(V::x).max().orElse(0);
        check(l.position().x()<=lo+1e-7||l.position().x()>=hi-1e-7,"renderer capped an empty lane across the ramp");
      }
    }
  }
  static void rails(){for(var style:List.of(Style.C1_RAMP,Style.C1_HIGHWAY_RAMP)){
    var raw=Live435Validation.road(style,Structure.BRIDGE);
    var md=LanePoints.Data.EMPTY.openings(List.of(new LanePoints.Opening(new UUID(436,1),List.of(new V(100,20,0),new V(100,20,200)),2)));
    var samples=raw.samples().stream().map(s->new Sample(s.center(),s.left(),s.distance(),2)).toList();
    var own=RoadRibbon.mesh(samples,raw.settings().options(raw.settings().options().lanePoints(md)));
    var neighbors=new ArrayList<Mesh>();
    for(int side:new int[]{-1,1})neighbors.add(RoadRibbon.mesh(samples.stream().map(s->new Sample(s.center().add(new V(side*3.98,-2,0)),s.left(),s.distance(),2)).toList(),raw.settings()));
    var join=new RoadRailJoin(neighbors.stream().map(m->new RoadRailJoin.Neighbor(m,true)).toList());
    Ground ground=new Ground(){public double top(double x,double z,double y){return 0;}public boolean joined(V p){return false;}
      public List<RoadRailJoin.Span> railSpans(V a,V b,V outside){return join.exposed(a,b,outside);}
      public boolean blocked(Part p){return neighbors.stream().anyMatch(m->RoadClearance.structureInvades(p,m,4.25));}};
    var parts=RoadStructures.plan(own,ground);
    for(int side:new int[]{-1,1})for(double z:new double[]{20.25,70.25,120.25,180.25})
      check(parts.stream().anyMatch(p->(p.material()==Material.STEEL&&Math.abs(p.a().y()-20.65)<.01||p.material()==Material.CONCRETE&&Math.abs(p.a().y()-20)<.01&&Math.abs(p.height()-.8)<.01)&&p.a().x()*side>1&&Math.min(p.a().z(),p.b().z())<=z&&Math.max(p.a().z(),p.b().z())>=z),"exposed guardrail deleted beside lower live lane");
    for(var p:parts)check(!ground.blocked(p),"restored rail invades adjacent live clearance");
  }}
  static void loops(){
    var settings=Live435Validation.road(Style.C1_RAMP,Structure.BRIDGE).settings();
    for(int mirror:new int[]{-1,1})for(int rotation=0;rotation<4;rotation++){
      double angle=rotation*Math.PI/2;V a=new V(Math.sin(angle),0,Math.cos(angle)),b=a.left().mul(mirror);
      V start=new V(100,20,200),end=start.add(a.mul(-160)).add(b.mul(160)).add(new V(0,8,0));
      var paths=LaneRampPaths.smoothTurns(new LaneRampPaths.Port(start,a,b,0,0),new LaneRampPaths.Port(end,b,a,0,0),settings,LanePoints.Options.DEFAULT,.2);
      check(!paths.isEmpty(),"round backward turn missing");
      var m=paths.get(0).mesh();LaneRampGrade.validate(m,.2);
      check(RoadRibbon.start(m).direction().dot(a)>.999&&RoadRibbon.end(m).direction().dot(b)>.999,"biarc changed lane directions");
      check(m.first().center().distance(start)<1e-6&&m.last().center().distance(end)<1e-6,"biarc moved selected points");
      check(RoadRibbon.minRadius(m)+1e-3>=24,"return violates requested minimum radius");
      check(m.length()<end.sub(start).horizontalLength()*3.5,"return expands into an unnecessarily large loop");
      double straight=0;for(int i=1;i<m.samples().size();i++)if(m.samples().get(i).left().dot(m.samples().get(i-1).left())>1-1e-10)straight+=m.samples().get(i).center().sub(m.samples().get(i-1).center()).horizontalLength();
      check(straight<m.length()*.01,"return dominated by straight segments");
    }
  }
  public static void main(String[] args){sealing();terminal();rails();loops();System.out.println("Live436Validation: "+checks+" checks PASS (actual core meshes/paint/solids; no GPU)");}
}
