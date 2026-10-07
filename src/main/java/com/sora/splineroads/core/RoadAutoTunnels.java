package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Local lining for a connector genuinely buried in original terrain. The fixed
 * four-block travel height matches RoadClearance; a thin ceiling fits the extra
 * 0.10 separation margin instead of silently changing the solved road grade.
 * Regions are persisted on structural parts, not rediscovered during rendering. */
public final class RoadAutoTunnels {
  private static final String PREFIX="sr:auto_tunnel:";
  public record Region(double from,double to){}
  public static List<Region> detect(Mesh mesh,Ground ground){
    if(mesh.settings().options().lanePoints().link()==null||mesh.settings().structure()==Structure.TUNNEL)return List.of();
    var out=new ArrayList<Region>();double begin=Double.NaN;
    for(double d=0;d<mesh.length();d+=2){
      double end=Math.min(mesh.length(),d+2);var sample=RoadStructures.sample(mesh,(d+end)/2);int covered=0;
      for(double side:new double[]{-.8,0,.8}){
        var point=sample.at(side*sample.halfWidth(),0);
        double top=ground.top(point.x(),point.z(),point.y()+6);
        if(Double.isFinite(top)&&top>=point.y()+4.2)covered++;
      }
      if(covered>=2){if(!Double.isFinite(begin))begin=d;}
      else if(Double.isFinite(begin)){out.add(new Region(begin,d));begin=Double.NaN;}
    }
    if(Double.isFinite(begin))out.add(new Region(begin,mesh.length()));
    return List.copyOf(out);
  }
  public static Mesh tube(Mesh mesh,Region r){
    var points=new ArrayList<Sample>();
    for(double d=r.from();d<r.to()-1e-7;d+=1)points.add(RoadStructures.sample(mesh,d));
    points.add(RoadStructures.sample(mesh,r.to()));
    var options=mesh.settings().options().infrastructure(RoadInfrastructure.Config.DEFAULT.headroom(4).gantry(RoadInfrastructure.Gantry.OFF));
    return RoadRibbon.mesh(points,mesh.settings().structure(Structure.TUNNEL).options(options));
  }
  public static List<Region> regions(List<Part> parts){
    var out=new LinkedHashSet<Region>();
    for(var part:parts)if(part.material()==Material.TUNNEL&&part.model().startsWith(PREFIX)){
      String[] range=part.model().substring(PREFIX.length()).split(":");
      if(range.length!=2)continue;
      try{double a=Double.parseDouble(range[0]),b=Double.parseDouble(range[1]);
        if(Double.isFinite(a)&&Double.isFinite(b)&&a>=0&&b>a)out.add(new Region(a,b));
      }catch(NumberFormatException ignored){}
    }
    return List.copyOf(out);
  }
  public static List<Part> enclose(Mesh mesh,Ground ground,List<Part> original){
    var ranges=detect(mesh,ground);if(ranges.isEmpty())return original;
    var out=new ArrayList<Part>();
    for(var part:original){
      double at=RoadQueries.horizontal(mesh,part.a().add(part.b()).mul(.5)).sample().distance();
      boolean inside=ranges.stream().anyMatch(r->at>=r.from()&&at<=r.to());
      // Outdoor barriers and light standards do not belong inside the lining.
      if(!inside||part.pier()||part.material()==Material.CONCRETE)out.add(part);
    }
    for(var range:ranges){
      String tag=PREFIX+range.from()+":"+range.to();
      for(double d=range.from();d<range.to()-1e-7;d+=2){
        var a=RoadStructures.sample(mesh,d);var b=RoadStructures.sample(mesh,Math.min(range.to(),d+2));
        for(int side:new int[]{-1,1})out.add(new Part(a.at(side*(a.halfWidth()+.2),mesh.settings().thickness()),
            b.at(side*(b.halfWidth()+.2),mesh.settings().thickness()),.4,4.075+mesh.settings().thickness(),false,Material.TUNNEL,
            a.left().mul(.2),b.left().mul(.2),tag));
        int bands=Math.max(1,(int)Math.ceil((Math.max(a.halfWidth(),b.halfWidth())*2+.8)/6));
        for(int i=0;i<bands;i++){
          double u=-1+2.0*i/bands,v=-1+2.0*(i+1)/bands,wa=a.halfWidth()+.4,wb=b.halfWidth()+.4;
          out.add(new Part(a.at(wa*(u+v)/2,-4),b.at(wb*(u+v)/2,-4),Math.max(wa,wb)*(v-u),.075,false,Material.TUNNEL,
              a.left().mul(wa*(v-u)/2),b.left().mul(wb*(v-u)/2),tag));
        }
        out.add(new Part(a.at(0,-4.005),b.at(0,-4.005),.22,.03,false,Material.LAMP,a.left().mul(.11),b.left().mul(.11),""));
      }
    }
    return List.copyOf(out);
  }
  private RoadAutoTunnels(){}
}
