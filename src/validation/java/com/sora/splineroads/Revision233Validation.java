package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.JunctionSpec.*;
import java.util.*;

public final class Revision233Validation {
  static int checks; static volatile long sink;
  static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
  static JunctionSpec clock(JunctionSpec s,Control control,int offset){return new JunctionSpec(s.center(),s.kind(),s.leftTraffic(),s.cornerRadius(),s.islandRadius(),s.ringLanes(),s.ringLaneWidth(),s.thickness(),control,s.greenSeconds(),s.yellowSeconds(),s.allRedSeconds(),offset,s.guides(),s.greenIsland(),s.outerRail(),s.arms());}
  static RoadRaster.Box box(LegacyRaster232.Box b){return new RoadRaster.Box(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1());}
  static long localRun(RoadRaster.Local next,LegacyRaster232.Local old,List<RoadRaster.Cell> cells,boolean fast,int repeat){
    long start=System.nanoTime(),sum=0;
    for(int n=0;n<repeat;n++)for(var c:cells)sum+=fast?next.boxes(c).size():old.boxes(new LegacyRaster232.Cell(c.x(),c.y(),c.z())).size();
    sink=sum;return System.nanoTime()-start;
  }
  public static void main(String[] args){
    for(boolean left:new boolean[]{false,true})for(double[] angles:new double[][]{{0,90},{0,120,240},{0,90,180,270}}){
      var s=Revision232Validation.fixture(left,angles);var plan=JunctionPlanner.plan(s);var cycle=plan.signals();
      int phases=(int)s.arms().stream().filter(a->a.incoming()>0).map(Arm::phase).distinct().count();
      check(cycle.periodSeconds()==phases*(s.greenSeconds()+s.yellowSeconds()+s.allRedSeconds()),"no added pedestrian-only phase");
      for(int arm=0;arm<angles.length;arm++)for(long tick=-400;tick<4000;tick++){
        int v=cycle.vehicle(cycle.pedestrianVehicleArm(arm),tick),p=cycle.pedestrian(arm,tick);
        check(p==(v==0?1:0),"pedestrian is red throughout associated vehicle green/yellow");
        check(cycle.vehicle(arm,tick)==cycle.vehicle(arm,tick+20L*cycle.periodSeconds()),"periodic world-clock state");
      }
      for(int arm=0;arm<angles.length;arm++){
        var p=plan.pieces().get(arm);var ped=p.structures().stream().filter(v->v.material()==RoadStructures.Material.SIGNAL_PEDESTRIAN).toList();
        var vehicle=plan.pieces().get(cycle.pedestrianVehicleArm(arm));
        boolean shared=ped.stream().anyMatch(head->{V front=head.b().sub(head.a()).horizontalUnit(),base=head.a().sub(front.mul(.19)).sub(new V(0,2.3,0));return vehicle.structures().stream().anyMatch(post->post.material()==RoadStructures.Material.CB_POST&&post.a().add(post.b()).mul(.5).distance(base)<1e-6);});
        check(cycle.pedestrianVehicleArm(arm)==arm||shared,"cross-arm controller is the physically shared vehicle mast");
      }
    }
    var fixture=Revision232Validation.fixture(false,0,90,180,270);
    var refs=new ArrayList<JunctionPlanner.Ref>();var pieces=new ArrayList<JunctionPlanner.Piece>();
    for(int n=0;n<20;n++){var ref=new JunctionPlanner.Ref(clock(fixture,Control.SIGNALS,n),0);refs.add(ref);pieces.add(ref.get());}
    for(int n=0;n<20;n++)check(refs.get(n).get()==pieces.get(n),"LRU eviction does not rebuild retained road piece");
    var off=new JunctionSignalCycle(clock(fixture,Control.NONE,0),List.of());check(off.vehicle(0,0)==0&&off.pedestrian(0,0)==0,"disabled controller does not illuminate green");
    var cycles=new ArrayList<JunctionSignalCycle>();for(int n=0;n<2000;n++)cycles.add(new JunctionSignalCycle(clock(fixture,Control.SIGNALS,n),List.of()));
    long at=System.nanoTime(),sum=0;for(int t=0;t<100;t++)for(var c:cycles)for(int arm=0;arm<4;arm++)sum+=c.vehicle(arm,t)+c.pedestrian(arm,t);sink=sum;
    System.out.printf(Locale.ROOT,"CITY CLOCK: 2,000 independent junctions, 1,600,000 head-state queries %.2f ms; no ticking entities or junction planning in lookup.%n",(System.nanoTime()-at)/1e6);
    int cellsChecked=0;Mesh benchmark=null;
    for(double angle:new double[]{0,.47,1.11})for(double slope:new double[]{0,.04,.15,.30}){
      V f=new V(Math.sin(angle),0,Math.cos(angle));var settings=new Settings(Mode.STRAIGHT,Style.O4_YELLOW,17,1,.35,90);
      var mesh=RoadGeometry.build(new Node(new V(-80.5,64,-80.25),0,0),new Node(new V(-80.5,64+60*slope,-80.25).add(f.mul(60)),0,0),settings);
      var a=LegacyRaster232.raster(mesh);var b=RoadRaster.raster(mesh);check(a.size()==b.size(),"full raster cell count identical");
      var local=new RoadRaster.Local(mesh,List.of());
      for(var e:a.entrySet()){
        var cell=new RoadRaster.Cell(e.getKey().x(),e.getKey().y(),e.getKey().z());var expected=e.getValue().stream().map(Revision233Validation::box).toList();
        check(expected.equals(b.get(cell)),"bit-identical full raster boxes");
        if(cellsChecked++%13==0)check(expected.equals(local.boxes(cell)),"local raster agrees with full raster");
      }
      benchmark=mesh;
    }
    var mesh=benchmark;var a=new LegacyRaster232.Local(mesh,List.of());var b=new RoadRaster.Local(mesh,List.of());
    var cells=RoadRaster.raster(mesh).keySet().stream().sorted(Comparator.comparingInt(RoadRaster.Cell::hashCode)).limit(384).toList();
    localRun(b,a,cells,false,2);localRun(b,a,cells,true,2);
    long old=Long.MAX_VALUE,next=Long.MAX_VALUE;
    for(int n=0;n<5;n++){old=Math.min(old,localRun(b,a,cells,false,3));next=Math.min(next,localRun(b,a,cells,true,3));}
    var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();long id=Thread.currentThread().getId();
    long start=bean.getThreadAllocatedBytes(id);localRun(b,a,cells,false,3);long oldBytes=bean.getThreadAllocatedBytes(id)-start;
    start=bean.getThreadAllocatedBytes(id);localRun(b,a,cells,true,3);long newBytes=bean.getThreadAllocatedBytes(id)-start;
    System.out.printf(Locale.ROOT,"LOCAL SLOPE RASTER: %d cold cells, 0.23.2 %.2f ms / %.2f MiB allocated; 0.23.3 %.2f ms / %.2f MiB allocated.%n",cells.size()*3,old/1e6,oldBytes/1048576.0,next/1e6,newBytes/1048576.0);
    System.out.printf("Revision 0.23.3 PASS: %,d checks; %,d exact raster cells; 20 retained junctions; 2,000 independent clocks.%n",checks,cellsChecked);
  }
}
