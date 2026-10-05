package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Actual shared deck, queries, collision, clearance and surface geometry. No game/GPU. */
public final class AnyLane405Validation {
  static int checks,cases;
  static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
  static Settings settings(Style s,boolean left){var o=RoadProfile.Options.DEFAULT.traffic(left);return new Settings(Mode.STRAIGHT,s,RoadProfile.width(s,o,4),1,.35,90).options(o);}
  static Mesh road(Settings s,double angle){V origin=new V(-1000,100,-2000),d=new V(Math.sin(angle),0,Math.cos(angle));return RoadGeometry.build(new Node(origin,RoadPlanner.yaw(d),0),new Node(origin.add(d.mul(420)),RoadPlanner.yaw(d),0),s);}
  static Mesh cut(Mesh raw,int slot){int sign=LanePoints.lane(raw,210,slot).sign();double begin=sign>0?70:350,end=sign>0?330:90;var cuts=LaneSections.derive(raw,List.of(new LaneSections.Event(new UUID(405,slot),LaneSections.Kind.TEMPORARY,slot,sign,begin,32,end)));var meta=raw.settings().options().lanePoints().cuts(cuts);return LaneSections.apply(new Mesh(raw.samples(),raw.settings().options(raw.settings().options().lanePoints(meta)),raw.min(),raw.max(),raw.length(),false,null));}
  static boolean covers(List<RoadRaster.Box> boxes,int x,int y,int z,V p){return boxes.stream().anyMatch(b->p.x()>x+b.x0()+1e-7&&p.x()<x+b.x1()-1e-7&&p.z()>z+b.z0()+1e-7&&p.z()<z+b.z1()-1e-7&&p.y()>y+b.y0()+1e-7&&p.y()<y+b.y1()-1e-7);}
  static double area(List<V> p){double a=0;for(int i=1;i<p.size()-1;i++){var u=p.get(i).sub(p.get(0));var v=p.get(i+1).sub(p.get(0));a+=u.x()*v.z()-u.z()*v.x();}return Math.abs(a)/2;}
  public static void main(String[]args){
    for(var style:List.of(Style.O1_ONE,Style.O3_ONE,Style.O4_ONE,Style.O2_RAIL,Style.O4_RAIL,Style.O6_GREEN,Style.O8_YELLOW))for(boolean left:new boolean[]{false,true})for(double angle:new double[]{0,.65}){
      var raw=road(settings(style,left),angle);int count=RoadProfile.catalog(style).lanes();
      for(int slot=0;slot<count;slot++){cases++;var mesh=cut(raw,slot);check(mesh.settings().options().lanePoints().cuts().get(0).temporary(),"temp tag missing");var local=new RoadRaster.Local(mesh,List.of());
        for(double d:new double[]{32,140.7,209.3,279.2,389}){V p=LanePoints.lane(raw,d,slot).position();boolean closed=LaneSections.removed(mesh,d,slot)>.999;check(RoadQueries.contains(mesh,p,0,.01)!=closed,"query still sees removed slot");
          double hit=RoadQueries.ray(mesh,p.add(new V(0,10,0)),new V(0,-1,0),20);check(Double.isFinite(hit)!=closed,"ray sees removed slot");
          V q=p.add(new V(.037,-.3,.041));int x=(int)Math.floor(q.x()),y=(int)Math.floor(q.y()),z=(int)Math.floor(q.z());var cell=new RoadRaster.Cell(x,y,z);
          var eager=RoadRaster.raster(mesh,cell).getOrDefault(cell,List.of());var lazy=local.boxes(cell);check(covers(eager,x,y,z,q)==covers(lazy,x,y,z,q),"local/eager physical body disagree");check(covers(lazy,x,y,z,q)!=closed,"actual collision occupies lane opening");
          for(int j=0;j<count;j++)if(j!=slot){V kept=LanePoints.lane(raw,d,j).position();check(RoadQueries.contains(mesh,kept,0,.01),"unselected lane disappears");check(LanePoints.lane(mesh,d,j).position().distance(kept)<1e-8,"unselected lane moved");}
        }
        // A solid test strip contained in the removed lane is no longer an obstacle to itself.
        var lane=LanePoints.lane(raw,190,slot);var other=RoadRibbon.mesh(List.of(new Sample(lane.position(),lane.direction().left(),0,.5),new Sample(LanePoints.lane(raw,230,slot).position(),lane.direction().left(),40,.5)),settings(Style.O1_ONE,left));
        check(!RoadClearance.contacts(raw,other).isEmpty(),"fixture not overlapping authored road");check(RoadClearance.contacts(mesh,other).isEmpty(),"clearance index still fills temporary hole");
        if(angle==0){var visible=RoadSurface.build(mesh,List.of(),List.of());double top=visible.pavement().stream().filter(f->f.color()==0xDCDCDC).mapToDouble(f->area(f.points())).sum();
          double expected=0;for(int i=1;i<mesh.samples().size();i++)for(var s:LaneDeck.strips(mesh,mesh.samples().get(i-1),mesh.samples().get(i)))expected+=area(List.of(s.al(),s.ar(),s.br(),s.bl()));check(Math.abs(top-expected)<1e-5,"visible top disagrees with physical strips");
          var hidden=RoadSurface.build(other,List.of(mesh),List.of(mesh));check(hidden.pavement().stream().anyMatch(f->f.color()==0xDCDCDC&&area(f.points())>.01),"host ownership erased ramp above opening");
        }
        int per=count/2;boolean edge=RoadProfile.catalog(style).twoWay()?slot==per-1||slot==count-1:slot==0||slot==count-1;
        if(!edge||count==1){boolean rejected=false;try{LaneSections.derive(raw,List.of(new LaneSections.Event(new UUID(9,slot),LaneSections.Kind.DEPART,slot,LanePoints.lane(raw,200,slot).sign(),200,32)));}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"DETACH outer/single-lane restriction accidentally removed");}
      }
    }
    System.out.println("AnyLane405Validation: "+cases+" any-slot/traffic/rotation cases; "+checks+" geometry/body/picking/paint checks PASS (actual core, not Minecraft/GPU)");
  }
}
