package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.JunctionSpec.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

public final class Junction20Validation {
  static int checks;
  static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
  public static JunctionSpec fixture(int count,Kind kind,boolean left,boolean skew) {
    var arms=new ArrayList<Arm>();
    for(int i=0;i<count;i++) {
      double angle=2*Math.PI*i/count+(skew?(i%2==0?.08:-.06):0);
      V f=new V(-Math.cos(angle),0,-Math.sin(angle)),p=f.mul(-90).add(new V(0,64,0));
      Style style=i%2==0?Style.O2_YELLOW:Style.O4_GREEN;
      var settings=new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.35,90);
      arms.add(JunctionSpec.arm(new Node(p,Math.toDegrees(Math.atan2(-f.x(),f.z())),0),f,settings,true,i));
    }
    return new JunctionSpec(new V(0,64,0),kind,left,6,12,2,4,1,Control.SIGNALS,20,3,1,0,true,true,arms);
  }
  private static boolean deck(JunctionPlanner.Plan plan,V p){
    var cell=new RoadRaster.Cell((int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z()));
    double x=p.x()-cell.x(),y=p.y()-cell.y(),z=p.z()-cell.z();
    for(var piece:plan.pieces())for(var b:new RoadRaster.Local(piece.mesh(),List.of()).boxes(cell))if(x>=b.x0()-1e-8&&x<=b.x1()+1e-8&&y>=b.y0()-1e-8&&y<=b.y1()+1e-8&&z>=b.z0()-1e-8&&z<=b.z1()+1e-8)return true;
    return false;
  }
  public static void main(String[] args) {
    for(int n=3;n<=6;n++)for(Kind kind:Kind.values())for(boolean left:new boolean[]{false,true})for(boolean skew:new boolean[]{false,true}) {
      var spec=fixture(n,kind,left,skew);var plan=JunctionPlanner.plan(spec);
      check(plan.movements().size()>=n,"all incoming approaches have an exit");
      check(deck(plan,new V(.13,63.8,.19))==(kind==Kind.INTERSECTION),"intersection center solid / roundabout central island has no asphalt deck");
      for(var move:plan.movements()) {
        check(move.targetLane()<spec.arms().get(move.to()).outgoing(),"target lane exists");
        check(JunctionPlanner.turn(spec,move.from(),move.to())==move.turn(),"turn agrees with destination");
        if(kind==Kind.ROUNDABOUT)for(V p:move.path())check(p.sub(spec.center()).horizontalLength()>=spec.islandRadius(),"circulating movement stays outside island");
      }
      for(int i=0;i<n;i++) {
        Mesh mesh=plan.pieces().get(i).mesh();
        for(double lateral:new double[]{-.8,0,.8}) {
          Sample mouth=mesh.last();V p=mouth.at(mouth.halfWidth()*lateral,.15);
          // The full approach section intersects the common road; no single-point ring contact.
          V next=p.add(spec.center().sub(mouth.center()).horizontalUnit().mul(.2));
          check(deck(plan,next),"all lanes meet common pavement at mouth "+n+" "+kind+" "+i);
        }
        check(JunctionPlanner.signal(spec,i,i*480L)==1,"individual approach green");
        check(JunctionPlanner.signal(spec,i,i*480L+400)==2,"yellow interval");
        check(JunctionPlanner.signal(spec,i,i*480L+460)==0,"all-red interval");
      }
      for(var piece:plan.pieces()) {
        var base=RoadSurface.build(piece.mesh(),List.of(),List.of());var surface=RoadSurface.custom(base,piece.mesh(),piece.paint());
        for(var face:surface.markings())for(V p:face.points())check(RoadGeometry.finite(p.x(),p.y(),p.z()),"paint clipped to nondegenerate triangles stays finite");
      }
    }
    var spec=fixture(4,Kind.INTERSECTION,false,false);var arms=new ArrayList<>(spec.arms());var a=arms.get(0);
    for(int mask:new int[]{1,2,4,6,3,5,7,8,15,0}){
      arms.set(0,a.lanes(List.of(JunctionSpec.Lane.AUTO.mask(mask))));var s=spec.arms(arms);var p=JunctionPlanner.plan(s);
      int actual=p.movements().stream().filter(m->m.from()==0).mapToInt(JunctionPlanner.Movement::turn).reduce(0,(x,y)->x|y);
      check(actual==mask,"all requested arrow combinations map to real movements");
    }
    var one=a.lanes(List.of(JunctionSpec.Lane.AUTO.mask(1).target(0,1,0)));arms.set(0,one);
    try{JunctionPlanner.plan(spec.arms(arms));throw new AssertionError("mismatched turn mapping accepted");}catch(IllegalArgumentException expected){checks++;}
    // A T-junction has no straight exit on its stem: automatic middle lanes must remain usable.
    var tArms=new ArrayList<Arm>();
    for(int i=0;i<3;i++) {
      double angle=i*Math.PI/2;V f=new V(-Math.cos(angle),0,-Math.sin(angle));
      var settings=new Settings(Mode.STRAIGHT,Style.O6_YELLOW,25,1,.35,90);
      tArms.add(JunctionSpec.arm(new Node(f.mul(-100).add(new V(0,64,0)),0,0),f,settings,true,i));
    }
    var tee=fixture(3,Kind.INTERSECTION,false,false).arms(tArms);var teePlan=JunctionPlanner.plan(tee);
    for(int lane=0;lane<3;lane++)check(JunctionPlanner.mask(tee,1,lane)!=0,"automatic T stem lane has a valid turn");
    check(deck(teePlan,new V(.13,63.8,.19)),"T center is solid");
    var asym=new Arm(a.endpoint(),a.inward(),a.external(),2,1,13,0,RoadProfile.Median.NONE,0,0,true,4,3,1.5,0,Collections.nCopies(2,Lane.AUTO),false);
    arms=new ArrayList<>(spec.arms());arms.set(0,asym);var asymmetric=JunctionPlanner.plan(spec.arms(arms));
    check(Math.abs(asymmetric.pieces().get(0).mesh().first().halfWidth()-6.5)<1e-9,"unattached arm uses its configured width at outer endpoint");
    arms.set(0,asym.attached(true));var attached=JunctionPlanner.plan(spec.arms(arms));
    check(Math.abs(attached.pieces().get(0).mesh().first().halfWidth()-a.external().width()/2)<1e-9,"attached arm keeps exact external width");
    var incoming=new Arm(a.endpoint(),a.inward(),a.external(),2,0,9,0,RoadProfile.Median.NONE,0,0,true,4,3,1.5,0,Collections.nCopies(2,Lane.AUTO),false);
    arms.set(0,incoming);var oneWay=JunctionPlanner.plan(spec.arms(arms));
    check(oneWay.movements().stream().noneMatch(m->m.to()==0),"one-way incoming arm never receives outgoing movement");
    System.out.println("Junction 0.20: "+checks+" checks passed (32 layout/traffic fixtures, lane masks, signals, collision, finite paint)");
  }
}
