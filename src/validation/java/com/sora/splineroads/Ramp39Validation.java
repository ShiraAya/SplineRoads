package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Regression tests exercise production geometry, not copied audit algorithms. JDK-only. */
public final class Ramp39Validation {
  private static int checks;
  private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
  private static void near(double a,double b,double eps,String message){check(Math.abs(a-b)<=eps,message+": "+a+" / "+b);}
  private static void denied(Runnable r,String message){try{r.run();}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError(message);}
  private static Settings settings(Style style,double lane,double thickness){return new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,RoadProfile.Options.DEFAULT,lane),thickness,.35,90);}
  private static Mesh road(V a,V b,Settings s){V d=b.sub(a);return RoadGeometry.build(new Node(a,RoadPlanner.yaw(d.horizontalUnit()),d.y()/d.horizontalLength()),new Node(b,RoadPlanner.yaw(d.horizontalUnit()),d.y()/d.horizontalLength()),s);}
  private static LanePoints.Link link(UUID source){return new LanePoints.Link(LanePoints.Ref.lane(source,new UUID(0,100)),LanePoints.Ref.lane(new UUID(0,1000),new UUID(0,200)),LanePoints.Options.DEFAULT,null);}
  private static Mesh metadata(Mesh mesh,LanePoints.Data data){return RoadRibbon.mesh(mesh.samples(),mesh.settings().options(mesh.settings().options().lanePoints(data)));}
  private static String key(RoadSurface.Face f){return f.color()+":"+f.points().stream().map(v->String.format(Locale.ROOT,"%.6f:%.6f:%.6f",v.x(),v.y(),v.z())).sorted().toList();}
  private static V rotate(V v,double angle){double c=Math.cos(angle),s=Math.sin(angle);return new V(v.x()*c-v.z()*s,v.y(),v.x()*s+v.z()*c);}
  private static LaneRampPaths.Port port(V p,V d){return new LaneRampPaths.Port(p,d,d.left(),4,0);}
  public static void main(String[] args){ownership();clearance();migration();paths();heights();sections();options();System.out.println("Ramp39Validation: "+checks+" checks passed (production core; no Forge/client claims)");}

  private static void ownership(){
    var raw=road(new V(0,100,0),new V(0,100,90),settings(Style.O1_ONE,4,1));
    UUID[] ids={new UUID(0,9),new UUID(0,5),new UUID(0,1)};
    Mesh[] meshes={metadata(raw,LanePoints.Data.EMPTY.link(link(new UUID(0,900))).priorityDepth(1)),metadata(raw,LanePoints.Data.EMPTY.link(link(ids[0])).priorityDepth(2)),metadata(raw,LanePoints.Data.EMPTY.link(link(ids[1])).priorityDepth(3))};
    for(int i=0;i<3;i++)for(int j=0;j<3;j++)for(int k=0;k<3;k++){
      boolean ij=RoadSurface.higherPriority(ids[i],meshes[i],ids[j],meshes[j]),jk=RoadSurface.higherPriority(ids[j],meshes[j],ids[k],meshes[k]);
      if(ij&&jk)check(RoadSurface.higherPriority(ids[i],meshes[i],ids[k],meshes[k]),"ownership is transitive");
      if(i==j)check(!ij,"strict ordering is irreflexive");
    }
    var allPaint=new HashSet<String>();int owners=0;
    for(int i=0;i<3;i++){
      var higher=new ArrayList<Mesh>();var neighbors=new ArrayList<Mesh>();
      for(int j=0;j<3;j++)if(i!=j){neighbors.add(meshes[j]);if(RoadSurface.higherPriority(ids[j],meshes[j],ids[i],meshes[i]))higher.add(meshes[j]);}
      var surface=RoadSurface.build(meshes[i],higher,neighbors);if(!surface.pavement().isEmpty())owners++;
      for(var face:surface.markings())check(allPaint.add(key(face)),"generated junction stripe has one owner");
    }
    check(owners==1,"three chained ramps cannot erase the entire shared deck");
    // Legacy unranked data must remain transitive too; reconcile supplies depth on load.
    for(int i=0;i<3;i++)meshes[i]=metadata(raw,LanePoints.Data.EMPTY.link(link(i==0?new UUID(0,900):ids[i-1])));
    check(!(RoadSurface.higherPriority(ids[0],meshes[0],ids[1],meshes[1])&&RoadSurface.higherPriority(ids[1],meshes[1],ids[2],meshes[2])&&RoadSurface.higherPriority(ids[2],meshes[2],ids[0],meshes[0])),"no legacy A>B>C>A fallback");
    Mesh fitted=metadata(raw,LanePoints.Data.EMPTY.link(link(ids[0])));
    for(int side:new int[]{-1,1})near(RoadSurface.edgeOffset(fitted,fitted.first(),side),RoadProfile.layout(fitted,fitted.first()).outer(side),1e-9,"mouth stripe follows actual motor boundary");
    near(LanePoints.lane(fitted,0,0).width(),4,1e-9,"stripe repair does not narrow motor lane");
  }

  private static void clearance(){
    for(double rotation:new double[]{0,.37,Math.PI/2})for(double origin:new double[]{0,20000000}){
      V shift=new V(origin,0,origin);
      var a=road(rotate(new V(0,100,0),rotation).add(shift),rotate(new V(0,100,100),rotation).add(shift),settings(Style.O1_ONE,4,.25));
      var b=road(rotate(new V(1,100,0),rotation).add(shift),rotate(new V(1,100,100),rotation).add(shift),settings(Style.O1_ONE,4,2));
      denied(()->RoadClearance.check(a,b),"unrelated same-height parallel material overlaps must fail");
      var lower=road(rotate(new V(-40,100,50),rotation).add(shift),rotate(new V(40,100,50),rotation).add(shift),settings(Style.O1_ONE,4,2));
      var upper=road(rotate(new V(0,104.75,0),rotation).add(shift),rotate(new V(0,104.75,100),rotation).add(shift),settings(Style.O1_ONE,4,.25));
      RoadClearance.check(upper,lower);RoadClearance.check(lower,upper);checks+=2;
      var contacts=RoadClearance.contacts(upper,lower);check(!contacts.isEmpty(),"actual crossing found including far-world coordinates");
      for(var c:contacts){near(c.usableClearance(),4.5,1e-5,"only upper deck thickness reduces free clearance");near(c.ours().x(),c.other().x(),1e-9,"collision positions share actual X");near(c.ours().z(),c.other().z(),1e-9,"collision positions share actual Z");}
    }
    var a=road(new V(0,100,0),new V(0,100,100),settings(Style.O1_ONE,4,1));
    var beside=road(new V(5,100,0),new V(5,100,100),settings(Style.O1_ONE,4,1));
    RoadClearance.check(a,beside);checks++;
    var unsafe=road(new V(-40,103.8,50),new V(40,103.8,50),settings(Style.O1_ONE,4,.25));denied(()->RoadClearance.check(a,unsafe),"genuinely inadequate clearance cannot pass");
    RoadClearance.check(a,a,a);checks++;
    check(RoadClearance.contacts(a,beside).equals(RoadClearance.contacts(a,RoadClearance.prepare(beside))),"prepared collision index agrees with one-shot query");
    var sloping=road(new V(0,98,0),new V(0,108,100),settings(Style.O1_ONE,4,1));
    check(RoadClearance.contacts(a,sloping).stream().anyMatch(c->c.blocked()&&Math.abs(c.ours().y()-c.other().y())<1e-6),"plane-crossing diagnostic identifies an actual equal-height contact");
  }

  private static void migration(){
    for(Style style:List.of(Style.O4_YELLOW,Style.O6_RAIL))for(boolean left:List.of(false,true))for(double angle:new double[]{0,.7,Math.PI}){
      var options=RoadProfile.Options.DEFAULT.traffic(left);var s=settings(style,4,1).options(options);
      var previous=road(rotate(new V(0,100,0),angle),rotate(new V(0,100,100),angle),s);
      var target=road(rotate(new V(0,100,200),angle),rotate(new V(0,100,50),angle),s);
      for(int index=0;index<RoadProfile.catalog(style).lanes();index++){
        var p=LanePoints.point(new UUID(0,index+1),LanePoints.Origin.MANUAL,previous,75,index);
        var moved=LanePoints.migrate(previous,target,p);
        near(moved.position().distance(p.position()),0,1e-6,"migrated slot keeps its world lane");
        near(LanePoints.lane(previous,p).direction().dot(LanePoints.lane(target,moved).direction()),1,1e-8,"migrated slot keeps driving direction");
        check(moved.id().equals(p.id()),"migrated point keeps identity");
      }
    }
  }

  private static void paths(){
    for(double rotation:new double[]{0,.6,1.5,3.1}){
      var a=port(rotate(new V(0,100,0),rotation),rotate(new V(0,0,1),rotation));
      var b=port(rotate(new V(200,100,200),rotation),rotate(new V(1,0,0),rotation));
      var option=new LanePoints.Options(LanePoints.Path.LEFT,false,false,32,32);
      var list=LaneRampPaths.candidates(a,b,settings(Style.O1_ONE,4,1),option);check(!list.isEmpty(),"directional left exists without a loop");
      for(var c:list){near(c.mesh().first().center().distance(a.position()),0,1e-6,"left starts on exact lane point");near(c.mesh().last().center().distance(b.position()),0,1e-6,"left ends on exact lane point");check(c.mesh().length()<450,"directional left does not become 270-degree loop");}
      var automatic=LaneRampPaths.candidates(a,b,settings(Style.O1_ONE,4,1),LanePoints.Options.DEFAULT);
      check(automatic.get(0).path()==LanePoints.Path.LEFT,"AUTO can choose the shorter genuine left");
    }
  }

  private static void heights(){
    Mesh base=road(new V(0,100,0),new V(0,100,700),settings(Style.O1_ONE,4,1));
    var obstacles=List.of(road(new V(-30,100,220),new V(30,100,220),settings(Style.O1_ONE,4,1)),road(new V(-30,101,400),new V(30,101,400),settings(Style.O1_ONE,4,.5)));
    for(boolean over:new boolean[]{true,false}){
      var constraints=new ArrayList<LaneRampHeights.Constraint>();
      for(var obstacle:obstacles)for(var c:RoadClearance.contacts(base,obstacle))constraints.add(new LaneRampHeights.Constraint(c.from(),c.to(),over?c.raise():c.lower()));
      var fitted=LaneRampHeights.solve(base,10,690,constraints,over);
      near(fitted.first().center().y(),100,1e-9,"crossing cannot move source Y");near(fitted.last().center().y(),100,1e-9,"crossing cannot move target Y");
      for(var obstacle:obstacles){RoadClearance.check(fitted,obstacle);checks++;}
      for(int i=1;i<fitted.samples().size();i++){V d=fitted.samples().get(i).center().sub(fitted.samples().get(i-1).center());check(Math.abs(d.y())<=.15001*d.horizontalLength(),"obstacle-shaped profile stays under grade cap");}
    }
    var steepSamples=new ArrayList<Sample>();
    for(var sample:base.samples()){double y=100+.149*Math.min(40,sample.distance());steepSamples.add(new Sample(new V(sample.center().x(),y,sample.center().z()),sample.left(),sample.distance(),sample.halfWidth()));}
    var withFixedThroat=RoadRibbon.mesh(steepSamples,base.settings());
    var raised=LaneRampHeights.solve(withFixedThroat,80,690,List.of(new LaneRampHeights.Constraint(400,410,5.1)),true);
    near(raised.first().center().y(),100,1e-9,"fixed steep source throat does not consume distant crossing grade budget");
    check(raised.samples().stream().anyMatch(p->p.center().y()>110.9),"distant obstacle receives its required rise");
    var before=base.samples();denied(()->LaneRampHeights.solve(base,10,690,List.of(new LaneRampHeights.Constraint(20,25,6)),true),"obstacle too near source must fail, not move the source");check(before.equals(base.samples()),"failed fitting does not mutate base");
  }

  private static void sections(){
    for(double rotation:new double[]{0,.5,Math.PI/2})for(int slot:new int[]{0,1}){
      Mesh plain=road(rotate(new V(0,100,0),rotation),rotate(new V(0,100,500),rotation),settings(Style.O2_ONE,4,1));
      var depart=new LaneSections.Event(new UUID(0,1),LaneSections.Kind.DEPART,slot,1,80,32);
      var replace=new LaneSections.Event(new UUID(0,2),LaneSections.Kind.REPLACE,slot,1,380,32);
      var cuts=LaneSections.derive(plain,List.of(replace,depart));
      Mesh raw=metadata(plain,LanePoints.Data.EMPTY.cuts(cuts));Mesh road=LaneSections.apply(raw);
      check(road.reference()==raw,"raw authored road remains intact and accessible");
      near(RoadStructures.sample(road,40).halfWidth()*2,9,1e-8,"before departure, two lanes retained");
      near(RoadStructures.sample(road,200).halfWidth()*2,5,1e-8,"detached lane actually removed from collision ribbon");
      near(RoadStructures.sample(road,430).halfWidth()*2,9,1e-8,"replacement restores two lanes");
      check(!LaneSections.active(road,200,slot)&&LaneSections.active(road,200,1-slot),"lane availability distinguishes reserved slot");
      var still=LanePoints.lane(road,200,1-slot);near(still.position().distance(LanePoints.lane(plain,200,1-slot).position()),0,1e-8,"continuing lane not shifted");
      var absent=LanePoints.lane(road,200,slot);check(!RoadQueries.contains(road,absent.position(),0,.1),"reserved slot has no invisible road collision");
      var p=LanePoints.point(new UUID(0,3),LanePoints.Origin.MANUAL,raw,200,slot);near(LanePoints.snap(road,p).position().distance(p.position()),0,1e-9,"manual reserved point does not jump into surviving lane");
      var view=LanePointPreview.render(road,p,240,120,slot);check(view.markers().size()==2,"point UI retains both selectable stable lane slots");
      var simplified=RoadRenderMesh.simplify(road);check(simplified.reference()==raw,"render simplification keeps section identity");
      near(RoadProfile.layout(simplified,RoadStructures.sample(simplified,200)).catalog().lanes(),1,0,"effective profile uses one active lane");
      Mesh restored=LaneSections.apply(metadata(plain,LanePoints.Data.EMPTY));check(restored.samples().equals(plain.samples()),"removing connector restores authored road without accumulated edits");
      var open=LaneSections.apply(metadata(plain,LanePoints.Data.EMPTY.cuts(LaneSections.derive(plain,List.of(depart)))));near(open.last().halfWidth()*2,5,1e-8,"without replacement, no ghost original lane returns at road end");
      denied(()->LaneSections.derive(plain,List.of(replace)),"replacement requires a real upstream departure");
      denied(()->LaneSections.derive(plain,List.of(depart,new LaneSections.Event(new UUID(0,4),LaneSections.Kind.DEPART,slot,1,150,32))),"cannot detach an already absent lane");
      var surface=RoadSurface.build(road,List.of(),List.of());check(!surface.pavement().isEmpty(),"section edit produces visible pavement");
    }
    Mesh single=road(new V(0,100,0),new V(0,100,100),settings(Style.O1_ONE,4,1));
    denied(()->LaneSections.derive(single,List.of(new LaneSections.Event(new UUID(0,5),LaneSections.Kind.DEPART,0,1,30,32))),"single-lane internal Y uses BRANCH rather than deleting its entire continuation");
  }
  private static void options(){
    var old=new LanePoints.Options(LanePoints.Path.AUTO,false,true,24,32);check(old.departure()==LanePoints.Departure.BRANCH&&old.arrival()==LanePoints.Arrival.EXTRA,"legacy booleans do not silently become destructive detachment");
    var fixed=new LanePoints.Options(LanePoints.Path.LEFT,LanePoints.Departure.DETACH,LanePoints.Arrival.REPLACE,24,32,LanePoints.Elevation.OVER,LanePoints.Landing.EXACT);
    check(!fixed.sourceExtra()&&!fixed.targetExtra()&&fixed.landing()==LanePoints.Landing.EXACT,"explicit topology and exact toll-port landing are independent of expansion");
  }
}
