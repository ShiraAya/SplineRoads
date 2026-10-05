package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.core.RoadInfrastructure.*;
import java.util.*;
import java.nio.file.*;

public final class Revision28Validation {
  static int checks,plans;
  static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
  static Ground ground(double height){return new Ground(){public double top(double x,double z,double y){return Math.min(height,y);}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};}
  static Settings settings(Style style,Structure kind,Config c){return new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.4,90).structure(kind).options(RoadProfile.Options.DEFAULT.infrastructure(c));}
  static Mesh mesh(Settings s,double length,boolean curved){return RoadGeometry.build(new Node(new V(0,18,0),-90,.02),new Node(new V(length,curved?22:18,curved?24:0),curved?-68:-90,curved?.02:0),curved?new Settings(Mode.CURVE,s.style(),s.width(),s.thickness(),s.tension(),s.arcDegrees()).structure(s.structure()).options(s.options()):s);}
  static boolean hits(Map<RoadRaster.Cell,List<RoadRaster.Box>> cells,V p){var cell=new RoadRaster.Cell((int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z()));return cells.getOrDefault(cell,List.of()).stream().anyMatch(b->p.x()-cell.x()>=b.x0()-1e-6&&p.x()-cell.x()<=b.x1()+1e-6&&p.y()-cell.y()>=b.y0()-1e-6&&p.y()-cell.y()<=b.y1()+1e-6&&p.z()-cell.z()>=b.z0()-1e-6&&p.z()-cell.z()<=b.z1()+1e-6);}
  static void geometry(Mesh m,List<Part> parts){
    plans++;check(!parts.isEmpty(),"infrastructure generated");
    for(var p:parts)for(var f:p.faces())for(var v:f.points())check(RoadGeometry.finite(v.x(),v.y(),v.z()),"finite mesh");
    var cells=RoadRaster.structures(parts,null);var local=new RoadRaster.Local(m,parts);
    for(var e:cells.entrySet())if(Math.floorMod(e.getKey().hashCode(),71)==0){
      var nearby=local.boxes(e.getKey());for(var b:e.getValue())check(nearby.stream().anyMatch(a->a.x0()<=b.x0()+1e-6&&a.x1()>=b.x1()-1e-6&&a.y0()<=b.y0()+1e-6&&a.y1()>=b.y1()-1e-6&&a.z0()<=b.z0()+1e-6&&a.z1()>=b.z1()-1e-6),"local collision retains sloped roof/structure");
    }
    for(double d=4;d<m.length()-4;d+=5){Sample s=RoadStructures.sample(m,d);for(int side:new int[]{-1,1})for(double y:new double[]{1.5,3.5,4.75})if(hits(cells,s.at(side*s.halfWidth()*.55,-y)))throw new AssertionError("vehicle corridor clear: "+m.settings().options().infrastructure().bridge()+" end="+m.last().center()+" at "+d+"/"+y+" hits "+parts.stream().filter(p->hits(RoadRaster.structures(List.of(p),null),s.at(side*s.halfWidth()*.55,-y))).toList());}
  }
  static void export(String name,Mesh m,List<Part> parts)throws Exception {
    String folder=System.getProperty("road.visuals");if(folder==null)return;
    Path file=Path.of(folder,name+".json");Files.createDirectories(file.getParent());
    var faces=new ArrayList<RoadSurface.Face>();var surface=RoadSurface.build(m,List.of(),List.of());faces.addAll(surface.pavement());faces.addAll(surface.markings());for(var p:parts)faces.addAll(p.faces());
    StringBuilder out=new StringBuilder("[");for(var f:faces){if(out.length()>1)out.append(',');out.append("{\"c\":").append(f.color()).append(",\"v\":[");boolean first=true;for(var v:f.points()){if(!first)out.append(',');first=false;out.append('[').append(v.x()).append(',').append(v.y()).append(',').append(v.z()).append(']');}out.append("]}");}Files.writeString(file,out.append(']').toString());
  }
  public static void main(String[] args)throws Exception {
    for(Bridge b:Bridge.values())for(boolean curve:new boolean[]{false,true}){
      Config c=Config.DEFAULT.grade(.2).bridge(b).gantry(Gantry.OFF);Mesh m=mesh(settings(Style.O4_YELLOW,Structure.BRIDGE,c),b==Bridge.VIADUCT?b.span*3:b.span,curve);var p=RoadStructures.plan(m,ground(0));geometry(m,p);
      if(b!=Bridge.STANDARD)check(p.stream().anyMatch(v->v.pier()&&v.height()>5),"grounded bridge foundations");
      if(b==Bridge.ARCH)check(p.stream().anyMatch(v->v.material()==Material.ARCH_STEEL),"arch silhouette");
      if(!curve)export(b.name(),m,p);
    }
    for(Tunnel t:Tunnel.values())for(Style style:new Style[]{Style.O2_YELLOW,Style.H6_RAIL})for(boolean curve:new boolean[]{false,true}){
      Config c=Config.DEFAULT.grade(.2).tunnel(t).headroom(6);Mesh m=mesh(settings(style,Structure.TUNNEL,c),48,curve);var p=RoadStructures.plan(m,ground(30));geometry(m,p);
      check(p.stream().anyMatch(Part::luminous),"tunnel lights");check(p.stream().noneMatch(v->v.material()==Material.GREEN||v.material()==Material.CB_POST),"no outdoor trees or lamp posts inside tube");
      var shell=RoadRaster.structures(p.stream().filter(v->v.material()==Material.TUNNEL).toList(),null);
      for(double d=3;d<m.length()-3;d+=3){Sample s=RoadStructures.sample(m,d);for(double u:new double[]{-.9,-.5,0,.5,.9}){
        double y=c.headroom()+RoadInfrastructure.tunnelRise(c,s.halfWidth())*Math.sqrt(1-u*u);
        check(hits(shell,s.at((s.halfWidth()+1.55)*u,-y-.35)),"continuous roof on grade and curve");
      }}
      if(!curve&&style==Style.O2_YELLOW)export("TUNNEL_"+t.name(),m,p);
    }
    for(Style style:new Style[]{Style.O2_YELLOW,Style.O2_ONE,Style.H4_RAIL,Style.H2_ONE})for(boolean left:new boolean[]{false,true}){
      var s=settings(style,Structure.BRIDGE,Config.DEFAULT.grade(.2)).options(RoadProfile.Options.DEFAULT.infrastructure(Config.DEFAULT.grade(.2)).traffic(left));Mesh m=mesh(s,80,true);var p=RoadInfrastructure.plan(m,ground(0));geometry(m,p);
      var gantry=RoadGantry.plan(m,ground(0));check(!gantry.isEmpty(),"automatic overhead gantry structure");
      check(gantry.stream().noneMatch(v->v.material()==Material.SIGN_GREEN||v.material()==Material.SIGN_BLUE||v.material()==Material.SIGN_WHITE),"retired built-in sign panels are not regenerated");
      if(style==Style.H4_RAIL&&!left)export("GANTRY",m,p);
    }
    var ordinary=mesh(settings(Style.O2_YELLOW,Structure.GROUND,Config.DEFAULT.grade(.2)),64,false);
    check(RoadInfrastructure.plan(ordinary,ground(18)).isEmpty(),"ordinary ground AUTO has no gantry");
    var highway=mesh(settings(Style.H4_RAIL,Structure.GROUND,Config.DEFAULT.grade(.2)),64,false);
    check(!RoadInfrastructure.plan(highway,ground(18)).isEmpty(),"ground highway AUTO has gantry");
    var base=RoadProfile.Options.DEFAULT.infrastructure(Config.DEFAULT.grade(.2).bridge(Bridge.CABLE).tunnel(Tunnel.ARCH).headroom(8));
    check(base.extras(true,true,true).traffic(true).lift(.6,2).outerRail(RoadProfile.OuterRail.ON).sidewalk(RoadSidewalks.Config.DEFAULT).route(RoadProfile.Routing.DEFAULT).ends(RoadTransitions.Ends.NONE).infrastructure().equals(base.infrastructure()),"unrelated edits retain all infrastructure settings");
    System.out.printf("Revision28 PASS %d checks / %d infrastructure plans%n",checks,plans);
  }
}
