package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Terrain structure is local and independent of the connector's OVER/UNDER route.
 * Persist roofed and open-cut ranges with their solids; rendering never queries terrain. */
public final class RoadAutoTunnels {
  private static final String PREFIX="sr:auto_tunnel:",OPEN="sr:auto_cut:";
  public static final double HEADROOM=4,MIN_COVER=5,WALL_LIP=.5;
  public record Region(double from,double to){}
  public record OpenRegion(double from,double to,double clearance){}
  public record Section(double from,double to,boolean roof,double leftDepth,double rightDepth,double clearance){}
  public static boolean below(Sample at,Ground ground){
    for(double u:new double[]{-.8,0,.8}){V p=at.at(u*at.halfWidth(),0);double h=ground.surface(p.x(),p.z(),p.y());if(Double.isFinite(h)&&h>p.y()+1e-5)return true;}
    return false;
  }
  public static List<Section> sections(Mesh mesh,Ground ground){
    if(mesh.settings().options().lanePoints().link()==null)return List.of();
    var out=new ArrayList<Section>();
    for(double d=0;d<mesh.length()-1e-7;d+=1){
      double end=Math.min(mesh.length(),d+1),mid=(d+end)/2;var at=RoadStructures.sample(mesh,mid);
      if(!below(at,ground))continue;
      boolean roof=true;double left=0,right=0,clearance=HEADROOM;
      // Cover is required over the complete roof, including its outside corners.
      // One exposed shoulder/portal makes this whole cross-section an open cut.
      for(double station:new double[]{d+1e-6,mid,end-1e-6}){
        var s=RoadStructures.sample(mesh,station);
        for(double u:new double[]{-1,-.5,0,.5,1}){
          V p=s.at(u*(s.halfWidth()+.4),0);double h=ground.surface(p.x(),p.z(),p.y());
          double depth=Double.isFinite(h)?Math.max(0,h-p.y()):0;
          roof&=depth>=MIN_COVER-1e-7;
          if(u<0)right=Math.max(right,depth);if(u>0)left=Math.max(left,depth);
          clearance=Math.max(clearance,depth+WALL_LIP);
        }
      }
      out.add(new Section(d,end,roof,left,right,Math.min(RoadStructures.MAX_DROP,clearance)));
    }
    return List.copyOf(out);
  }
  public static List<Region> detect(Mesh mesh,Ground ground){
    var out=new ArrayList<Region>();
    for(var section:sections(mesh,ground))if(section.roof()){
      if(!out.isEmpty()&&Math.abs(out.get(out.size()-1).to()-section.from())<1e-7){var old=out.remove(out.size()-1);out.add(new Region(old.from(),section.to()));}
      else out.add(new Region(section.from(),section.to()));
    }
    return List.copyOf(out);
  }
  public static Mesh tube(Mesh mesh,Region r){return tube(mesh,r,HEADROOM);}
  public static Mesh tube(Mesh mesh,Region r,double height){
    var points=new ArrayList<Sample>();
    for(double d=r.from();d<r.to()-1e-7;d+=1)points.add(RoadStructures.sample(mesh,d));
    points.add(RoadStructures.sample(mesh,r.to()));
    var options=mesh.settings().options().infrastructure(RoadInfrastructure.Config.DEFAULT.headroom(Math.max(4,Math.min(12,height))).gantry(RoadInfrastructure.Gantry.OFF));
    return RoadRibbon.mesh(points,mesh.settings().structure(Structure.TUNNEL).options(options));
  }
  public static List<Region> regions(List<Part> parts){
    var out=new LinkedHashSet<Region>();
    for(var p:parts)if(p.material()==Material.TUNNEL&&p.model().startsWith(PREFIX)){
      double[] values=parse(p.model(),PREFIX,2);if(values!=null)out.add(new Region(values[0],values[1]));
    }
    return List.copyOf(out);
  }
  public static List<OpenRegion> openRegions(List<Part> parts){
    var out=new LinkedHashSet<OpenRegion>();
    for(var p:parts)if(p.material()==Material.TUNNEL&&p.model().startsWith(OPEN)){
      double[] values=parse(p.model(),OPEN,3);if(values!=null&&values[2]>=4&&values[2]<=RoadStructures.MAX_DROP)out.add(new OpenRegion(values[0],values[1],values[2]));
    }
    return List.copyOf(out);
  }
  private static double[] parse(String tag,String prefix,int count){
    String[] pieces=tag.substring(prefix.length()).split(":");if(pieces.length!=count)return null;
    try{double[] values=new double[count];for(int i=0;i<count;i++){values[i]=Double.parseDouble(pieces[i]);if(!Double.isFinite(values[i]))return null;}
      return values[0]>=0&&values[1]>values[0]?values:null;
    }catch(NumberFormatException ignored){return null;}
  }
  public static List<Part> enclose(Mesh mesh,Ground ground,List<Part> original){
    var sections=sections(mesh,ground);if(sections.isEmpty())return original;
    var out=new ArrayList<Part>();
    for(var part:original){
      double at=RoadQueries.horizontal(mesh,part.a().add(part.b()).mul(.5)).sample().distance();
      boolean inside=sections.stream().anyMatch(r->at>=r.from()&&at<=r.to());
      // No bridge columns, outdoor rails or lamp posts inside the underground lining.
      if(!inside)out.add(part);
    }
    // Coalesce equal roofed sections so persisted air-reservation meshes stay small.
    var roofed=new ArrayList<Region>();
    for(var s:sections)if(s.roof()){
      if(!roofed.isEmpty()&&Math.abs(roofed.get(roofed.size()-1).to()-s.from())<1e-7){var r=roofed.remove(roofed.size()-1);roofed.add(new Region(r.from(),s.to()));}
      else roofed.add(new Region(s.from(),s.to()));
    }
    for(var s:sections){
      var a=RoadStructures.sample(mesh,s.from());var b=RoadStructures.sample(mesh,s.to());
      var range=s.roof()?roofed.stream().filter(r->s.from()>=r.from()&&s.to()<=r.to()).findFirst().orElseThrow():null;
      String tag=s.roof()?PREFIX+range.from()+":"+range.to():OPEN+s.from()+":"+s.to()+":"+s.clearance();
      for(int side:new int[]{-1,1}){
        double top=s.roof()?HEADROOM+.075:Math.max(WALL_LIP,(side>0?s.leftDepth():s.rightDepth())+WALL_LIP);
        out.add(new Part(a.at(side*(a.halfWidth()+.2),mesh.settings().thickness()),b.at(side*(b.halfWidth()+.2),mesh.settings().thickness()),
            .4,Math.min(RoadStructures.MAX_DROP,top)+mesh.settings().thickness(),false,Material.TUNNEL,a.left().mul(.2),b.left().mul(.2),tag));
      }
      if(!s.roof())continue;
      int bands=Math.max(1,(int)Math.ceil((Math.max(a.halfWidth(),b.halfWidth())*2+.8)/6));
      for(int i=0;i<bands;i++){
        double u=-1+2.0*i/bands,v=-1+2.0*(i+1)/bands,wa=a.halfWidth()+.4,wb=b.halfWidth()+.4;
        out.add(new Part(a.at(wa*(u+v)/2,-HEADROOM),b.at(wb*(u+v)/2,-HEADROOM),Math.max(wa,wb)*(v-u),.075,false,Material.TUNNEL,
            a.left().mul(wa*(v-u)/2),b.left().mul(wb*(v-u)/2),tag));
      }
      out.add(new Part(a.at(0,-HEADROOM-.005),b.at(0,-HEADROOM-.005),.22,.03,false,Material.LAMP,a.left().mul(.11),b.left().mul(.11),""));
    }
    return List.copyOf(out);
  }
  private RoadAutoTunnels(){}
}
