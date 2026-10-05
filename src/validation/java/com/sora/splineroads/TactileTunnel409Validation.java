package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Actual generated geometry, no Minecraft or GPU emulation. */
public final class TactileTunnel409Validation {
  static int checks,cases;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
  static JunctionSpec fixture(double[] angles,int width,double radius,double rotation,boolean mirror){
    var arms=new ArrayList<JunctionSpec.Arm>();
    for(int i=0;i<angles.length;i++){double angle=Math.toRadians((mirror?-angles[i]:angles[i])+rotation);var d=new V(-Math.cos(angle),0,-Math.sin(angle));
      var walk=new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,width,"minecraft:stone_bricks");
      var o=RoadProfile.Options.DEFAULT.sidewalk(walk);var s=new Settings(Mode.STRAIGHT,Style.O4_YELLOW,RoadProfile.width(Style.O4_YELLOW,o,4),1,.35,90).options(o);
      arms.add(JunctionSpec.arm(new Node(d.mul(-100).add(new V(0,2,0)),RoadPlanner.yaw(d),0),d,s,true,i));}
    return new JunctionSpec(new V(0,2,0),JunctionSpec.Kind.INTERSECTION,false,radius,24,2,4,1,JunctionSpec.Control.SIGNALS,20,3,1,0,false,false,arms);
  }
  static double reversal(List<Part> parts){double signed=0,absolute=0,worst=0;Part prev=null;
    for(var p:parts){if(prev==null||prev.b().distance(p.a())>.001){worst=Math.max(worst,absolute-Math.abs(signed));signed=absolute=0;}
      else {var a=prev.b().sub(prev.a()).horizontalUnit();var b=p.b().sub(p.a()).horizontalUnit();double turn=Math.atan2(a.x()*b.z()-a.z()*b.x(),a.dot(b));signed+=turn;absolute+=Math.abs(turn);}prev=p;}
    return Math.max(worst,absolute-Math.abs(signed))*180/Math.PI;
  }
  public static void main(String[] args){
    double worst=0;
    for(var angles:List.of(new double[]{0,80,285},new double[]{0,40,235},new double[]{0,30,210},new double[]{0,120,240},new double[]{0,90,180,270}))
    for(int width:new int[]{3,5})for(double radius:new double[]{2,8})for(double rotation:new double[]{0,37})for(boolean mirror:new boolean[]{false,true}){
      var spec=fixture(angles,width,radius,rotation,mirror);var plan=JunctionPlanner.plan(spec);
      var approaches=plan.pieces().subList(0,angles.length).stream().map(JunctionPlanner.Piece::mesh).toList();
      var corners=TactilePaths.junction(spec,approaches,plan.boundary(),true).stream().filter(p->p.material()==Material.TACTILE&&p.height()<.018&&!p.pier()).toList();
      check(!corners.isEmpty(),"fix must not hide tactile geometry");double turn=reversal(corners);worst=Math.max(worst,turn);
      check(turn<.01,"uniform-width corner bends inward then back: "+Arrays.toString(angles)+" width="+width+" radius="+radius+" mirror="+mirror+" turn="+turn);
      var again=TactilePaths.junction(spec,approaches,plan.boundary(),true);check(again.containsAll(corners),"deterministic corner production");
      for(var p:corners)for(var v:p.base())check(Double.isFinite(v.x()+v.y()+v.z()),"finite tactile base");cases++;
    }
    var ground=new Ground(){public double top(double x,double z,double y){return y;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
    for(var style:List.of(Style.O2_YELLOW,Style.O4_YELLOW,Style.O4_RAIL,Style.O4_GREEN,Style.H4_GREEN)){
      var s=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,RoadProfile.Options.DEFAULT,4),1,.35,90).structure(Structure.TUNNEL);
      var m=RoadGeometry.build(new Node(new V(0,100,0),0,0),new Node(new V(0,100,96),0,0),s);var parts=RoadInfrastructure.plan(m,ground);
      check(parts.stream().noneMatch(p->p.material()==Material.GREEN||p.material()==Material.SOIL),"no soil/leaves in tunnel median");
      var layout=RoadProfile.layout(m,m.first());var authored=RoadProfile.layout(s,2*m.first().halfWidth());
      check(layout.motorMin()==authored.motorMin()&&layout.motorMax()==authored.motorMax()&&layout.laneWidth()==authored.laneWidth(),"material mapping preserves authored lane axes");
      if(RoadProfile.catalog(style).median()==RoadProfile.Median.GREEN){check(layout.catalog().median()==RoadProfile.Median.RAIL,"green maps to rail");check(parts.stream().anyMatch(p->p.material()==Material.STEEL),"mapped rail is physical geometry");}
      else check(layout.catalog().median()==RoadProfile.catalog(style).median(),"yellow/rail selection preserved");
    }
    System.out.println("TactileTunnel409Validation: "+cases+" real junction cases, "+checks+" checks; max uniform-row reversal="+worst+" deg. No game/GPU validation.");
  }
}
