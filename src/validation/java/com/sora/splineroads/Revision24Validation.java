package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.core.JunctionSpec.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import java.util.*;

/** Reproduces 0.24 reports with geometry, independent clocks and complete movement coverage. */
public final class Revision24Validation {
  static int checks;
  static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
  public static Node[] nodes(int n,double radius,double rotate,double x,double y,double z){
    double[] angles=n==5?new double[]{0,180,90,270,45}:new double[]{0,180,60,240,120,300};
    Node[] nodes=new Node[n];for(int i=0;i<n;i++){double a=Math.toRadians(angles[i]+rotate);nodes[i]=new Node(new V(x+Math.cos(a)*radius,y,z+Math.sin(a)*radius),0,0);}return nodes;
  }
  public static Settings main(Style style){return new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.4,90);}
  static void signals(){
    for(boolean left:new boolean[]{false,true})for(int arms:new int[]{2,3,4,5,6}){
      double[] angles=new double[arms];for(int i=0;i<arms;i++)angles[i]=360.0*i/arms;
      JunctionSpec spec=Junction22Validation.fixture(Style.O4_RAIL,left,angles);var changed=new ArrayList<Arm>();
      for(Arm arm:spec.arms()){var lanes=new ArrayList<>(arm.lanes());lanes.set(0,new Lane(JunctionSpec.LEFT|JunctionSpec.STRAIGHT|JunctionSpec.RIGHT,List.of(-1,-1,-1,-1),List.of(-1,-1,-1,-1),true,-1));changed.add(arm.lanes(lanes));}
      // Use automatic permitted turns where the two-arm case has no left destination.
      if(arms==2){var a=new ArrayList<Arm>();for(Arm arm:changed){var lanes=new ArrayList<>(arm.lanes());lanes.set(0,lanes.get(0).mask(-1));a.add(arm.lanes(lanes));}changed=a;}
      spec=spec.arms(changed);if(arms>2){
        // Some regular five-way arms have no straight target; do not request unavailable turns.
        var a=new ArrayList<Arm>();for(int i=0;i<arms;i++){int mask=0;for(int j=0;j<arms;j++)if(j!=i)mask|=JunctionPlanner.turn(spec,i,j);var lanes=new ArrayList<>(spec.arms().get(i).lanes());lanes.set(0,lanes.get(0).mask(mask));a.add(spec.arms().get(i).lanes(lanes));}spec=spec.arms(a);
      }
      var plan=JunctionPlanner.plan(spec);var cycle=plan.signals();int leftHeads=0;
      for(var piece:plan.pieces())if(piece.arm()>=0){
        var ref=new JunctionPlanner.Ref(spec,piece.arm());
        var heads=piece.structures().stream().filter(RoadSignals::signal).toList();
        for(Part head:heads)if(head.material()==Material.SIGNAL_LEFT){
          leftHeads++;var binding=plan.heads().get(head);check(binding!=null&&binding.left()&&binding.lane()==0,"left head belongs to the selected lane");
          boolean green=false,red=false,yellow=false;
          for(long t=0;t<cycle.periodSeconds()*20L;t+=20){int value=ref.headState(head,t);green|=value==1;red|=value==0;yellow|=value==2;check(value==0||cycle.vehicle(piece.arm(),t)==0,"default protected left and normal signals never overlap");}
          check(green&&red&&yellow,"left head has a complete signal cycle");
          V front=head.b().sub(head.a()).horizontalUnit();
          for(var f:RoadJunctionSignalModel.faces(head,true,1))if(f.emissive()){
            V a=f.points().get(1).sub(f.points().get(0)),b=f.points().get(2).sub(f.points().get(0));
            V normal=new V(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());
            check(normal.dot(front)>0,"left arrow luminous face points to approaching driver");
          }
        }
        for(long t=0;t<cycle.periodSeconds()*20L;t+=20){
          int control=cycle.pedestrianVehicleArm(piece.arm());boolean active=cycle.vehicle(control,t)!=0;
          for(int lane=0;lane<spec.arms().get(control).incoming();lane++)active|=cycle.vehicle(control,lane,true,t)!=0;
          check(cycle.pedestrian(piece.arm(),t)==(active?0:1),"pedestrians oppose every active head on their associated mast");
        }
      }
      check(arms==2?leftHeads==0:leftHeads==arms,"exactly the configured eligible lanes get an arrow head");
    }
  }
  static RoadStructures.Ground ground(java.util.function.ToDoubleFunction<V> height){return new RoadStructures.Ground(){public double top(double x,double z,double y){return height.applyAsDouble(new V(x,y,z));}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};}
  static void rails(){
    Settings s=main(Style.O4_YELLOW).structure(Structure.AUTO);Mesh mesh=RoadGeometry.build(new Node(new V(0,64,0),0,0),new Node(new V(120,64,0),0,0),s);
    for(var ground:List.of(ground(p->63.9375),ground(p->Math.abs(p.x()-60)<.25?Double.NaN:64))){
      var parts=RoadStructures.plan(mesh,ground);
      check(parts.stream().noneMatch(p->p.material()==Material.STEEL&&p.height()<.3&&p.a().y()<66),"surface quantization or one missing column does not spawn automatic rails");
    }
    var high=RoadStructures.plan(mesh,ground(p->60));check(high.stream().anyMatch(p->p.material()==Material.CONCRETE&&p.width()==.42),"real raised edge keeps bridge rails");
    for(boolean highway:new boolean[]{false,true})for(boolean raised:new boolean[]{false,true}){
      var parts=new ArrayList<Part>();V a=new V(0,64,0),b=new V(7.37,64,2.15);RoadStructures.barrier(parts,a,b,highway,raised,1.73);RoadStructures.terminalPosts(parts);
      if(!(highway&&raised))for(V p:List.of(a,b))check(parts.stream().anyMatch(q->q.material()==Material.DARK_STEEL&&q.a().sub(p).horizontalLength()<.001),"both open rail ends have terminal uprights");
    }
    var green=main(Style.O4_GREEN);var rail=main(Style.O4_RAIL);green=RoadTransitions.ends(green,RoadTransitions.Section.of(rail),null);
    mesh=RoadGeometry.build(new Node(new V(0,64,0),0,0),new Node(new V(160,64,0),0,0),green);
    var parts=RoadStructures.plan(mesh,ground(p->64));
    for(Part soil:parts)if(soil.material()==Material.SOIL)for(Part beam:parts)if(beam.material()==Material.STEEL&&beam.a().y()<66&&Math.abs(beam.a().z())<.5){
      double overlap=Math.min(Math.max(soil.a().x(),soil.b().x()),Math.max(beam.a().x(),beam.b().x()))-Math.max(Math.min(soil.a().x(),soil.b().x()),Math.min(beam.a().x(),beam.b().x()));
      check(overlap<=1e-7,"green-to-rail transition has no overlapping planted and railing spans");
    }
  }
  static void paint(){
    for(boolean left:new boolean[]{false,true})for(Kind kind:Kind.values()){
      var s=Revision232Validation.fixture(left,0,90,180,270);
      s=new JunctionSpec(s.center(),kind,left,s.cornerRadius(),24,3,4,1,s.control(),20,3,1,0,true,true,s.arms());
      var plan=JunctionPlanner.plan(s);
      for(var piece:plan.pieces())if(piece.arm()>=0){
        Arm arm=s.arms().get(piece.arm());Mesh mesh=piece.mesh();double overlap=kind==Kind.ROUNDABOUT?36-mesh.last().center().sub(s.center()).horizontalLength():0;
        double stop=mesh.length()-overlap-arm.crossingSetback()-arm.crossingWidth()-arm.stopGap();int tips=0;
        for(var f:piece.paint())if(f.points().size()==3){tips++;for(V p:f.points()){
          var q=RoadQueries.horizontal(mesh,p);check(q.sample().distance()<stop-.3,"complete arrow stays behind stop line");check(Math.abs(q.lateral())<q.sample().halfWidth()-arm.cycleWidth()-arm.curbWidth()-.1,"arrow never enters curb or bicycle strip");
        }}
        check(tips>=arm.incoming(),"incoming lanes retain complete readable arrows");
      }
    }
  }
  static void multi(){
    for(int n:new int[]{5,6})for(boolean left:new boolean[]{false,true})for(int lanes:new int[]{1,2}){
      Settings highway=main(Style.H4_RAIL),ordinary=main(Style.O4_YELLOW);Settings[] profiles=left?new Settings[]{ordinary,highway,ordinary}:new Settings[]{highway,highway,highway};
      var options=new Options(n==5?Preset.DIRECTIONAL_FIVE:Preset.DIRECTIONAL_SIX,left,lanes,96,20,5,2,0,true,false,0);
      var plan=MultiInterchange.plan(nodes(n,640,0,0,64,0),profiles,options,64);check(plan.legs().size()==(n==5?19:27),"one complete directional layout per arm count");
      var movements=new HashSet<String>();for(var leg:plan.legs()){
        check(RoadGrades.maximum(leg.mesh())<=.150001,"every main and ramp obeys 15 percent grade");
        check(leg.mesh().settings().options().leftTraffic()==left,"traffic direction applied to every deck");
        if(leg.mesh().settings().style().ramp()){check(movements.add(leg.from()+":"+leg.to()),"each directed turn exists once");check(RoadProfile.catalog(leg.mesh().settings().style()).lanes()==lanes,"ramp lane count retained");}
      }
      for(int i=0;i<n;i++)for(int j=0;j<n;j++)if(i!=j)check(i/2==j/2||movements.contains(i+":"+j),"every destination reachable directly or via through mainline");
      check(plan.minRadius()>=20-.0001,"minimum centerline radius respected");
      check(plan.anchors().size()==n,"no phantom selected endpoint");
      var again=MultiInterchange.plan(plan.anchors().toArray(Node[]::new),profiles,options,64);
      for(int i=0;i<n;i++)check(plan.anchors().get(i).position().distance(again.anchors().get(i).position())<1e-6,"reopening a fitted interchange never lifts or expands it again");
      System.out.printf(Locale.ROOT,"MULTI %d-way %s %d lane: %d turns, radius %.1f, highest %.1f, endpoint %.1f%n",n,left?"left/mixed":"right/highway",lanes,plan.movements(),plan.minRadius(),plan.highest(),plan.anchors().get(0).position().sub(plan.center()).horizontalLength());
    }
    for(int n:new int[]{5,6}){
      Settings s=main(Style.H4_RAIL);var options=new Options(n==5?Preset.DIRECTIONAL_FIVE:Preset.DIRECTIONAL_SIX,false,1,96,20,5,2,0,true,true,0);
      var first=MultiInterchange.plan(nodes(n,900,13,0,64,0),new Settings[]{s,s,s},options,64);
      var again=MultiInterchange.plan(first.anchors().toArray(Node[]::new),new Settings[]{s,s,s},options,64);
      check(first.anchors().get(0).position().sub(first.center()).horizontalLength()<890,"default compact fitting reduces a large layout");
      check(first.anchors().equals(again.anchors()),"compact default remains stable on reopen and save");
      System.out.printf(Locale.ROOT,"COMPACT %d-way: endpoint radius %.1f, turns %d%n",n,first.anchors().get(0).position().sub(first.center()).horizontalLength(),first.movements());
    }
    check(InterchangePlanner.presets(5).size()==1&&InterchangePlanner.presets(6).size()==1,"one preset each; legacy three/four-way catalog unchanged");
  }
  public static void main(String[] args){signals();rails();paint();multi();System.out.printf("Revision 0.24 PASS: %,d checks.%n",checks);}
}
